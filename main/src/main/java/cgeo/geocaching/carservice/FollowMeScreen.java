package cgeo.geocaching.carservice;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.SearchResult;
import cgeo.geocaching.enumerations.CacheListType;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Units;
import cgeo.geocaching.maps.CacheMarker;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.utils.AndroidRxUtils;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.MapMarkerUtils;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.CarLocation;
import androidx.car.app.model.Distance;
import androidx.car.app.model.DistanceSpan;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Metadata;
import androidx.car.app.model.Place;
import androidx.car.app.model.PlaceListMapTemplate;
import androidx.car.app.model.PlaceMarker;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.core.graphics.drawable.IconCompat;
import static androidx.car.app.model.PlaceMarker.TYPE_IMAGE;

import java.util.Set;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

@RequiresApi(23)
public class FollowMeScreen extends Screen {

    private ItemList itemList = null;
    private FusedLocationProviderClient fusedLocationClient;
    private final Object lock = new Object();
    private boolean isLoading;
    private Geopoint lastLocation = null;

    public FollowMeScreen(@NonNull final CarContext carContext) {
        super (carContext);
        initLocationUpdates();
    }

    private void initLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(getCarContext());
        final LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000) // Update every 5 seconds
                .setFastestInterval(2000);
        final LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull final LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    updatePoiList(new Geopoint(location.getLatitude(), location.getLongitude()));
                }
            }
        };
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        return new PlaceListMapTemplate.Builder()
            .setTitle("c:geo cache view")
            .setItemList(itemList == null ? new ItemList.Builder().build() : itemList)
            .setCurrentLocationEnabled(true)
            .setHeaderAction(Action.BACK)
            .build();
    }

    private void updatePoiList(final Geopoint geopoint) {
        synchronized (lock) {
            if (isLoading || (lastLocation != null && lastLocation.distanceTo(geopoint) < 0.05)) {
                return;
            }
            isLoading = true;
        }
        AndroidRxUtils.computationScheduler.scheduleDirect(() -> {
            // @todo: add live cache loading
            final SearchResult geocodes = DataStore.getBatchOfStoredCaches(new Geopoint(52.5, 13.4 /* geopoint.getLatitude(), geopoint.getLongitude() */), -1, null, null, false, 20);
            final Set<Geocache> caches = DataStore.loadCaches(geocodes.getGeocodes(), LoadFlags.LOAD_CACHE_OR_DB);
            Log.e("location: " + geopoint + ", loaded caches: " + caches.size());

            final ItemList.Builder itemListBuilder = new ItemList.Builder();
            for (Geocache cache : caches) {
                final CacheMarker marker = MapMarkerUtils.getCacheMarker(CgeoApplication.getInstance().getResources(), cache, CacheListType.OFFLINE, false);
                final CarIcon icon = new CarIcon.Builder(IconCompat.createWithBitmap(marker.getBitmap())).build();
                final double distance = cache.getCoords().distanceTo(geopoint);
                final DistanceSpan distanceSpan = DistanceSpan.create(Distance.create(distance, Distance.UNIT_KILOMETERS));
                final SpannableString distanceInfo = new SpannableString(Units.getDistanceFromKilometers((float) distance));
                distanceInfo.setSpan(distanceSpan, 0, distanceInfo.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                itemListBuilder.addItem(new Row.Builder()
                        .setTitle(cache.getName())
                        .addText(distanceInfo)
                        .setOnClickListener(() -> {
                            final String uri = "geo:" + cache.getCoords().getLatitude() + "," + cache.getCoords().getLongitude(); // cannot use a label, as some apps use label text as search query
                            getCarContext().startCarApp(new Intent(CarContext.ACTION_NAVIGATE, Uri.parse(uri)));
                        })
                        .setMetadata(new Metadata.Builder()
                                .setPlace(new Place.Builder(CarLocation.create(cache.getCoords().getLatitude(), cache.getCoords().getLongitude()))
                                        .setMarker(new PlaceMarker.Builder()
                                                .setIcon(icon, TYPE_IMAGE)
                                                .build())
                                        .build())
                                .build())
                        .build());
            }
            itemList = itemListBuilder.build();
            invalidate();
            synchronized (lock) {
                lastLocation = geopoint;
                isLoading = false;
            }
        });
    }

}
