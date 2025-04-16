package cgeo.geocaching.carservice;

import cgeo.geocaching.list.StoredList;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.storage.extension.Trackfiles;
import cgeo.geocaching.utils.AndroidRxUtils;
import cgeo.geocaching.utils.Log;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.CarLocation;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Metadata;
import androidx.car.app.model.Place;
import androidx.car.app.model.PlaceMarker;
import androidx.car.app.model.Row;
import androidx.car.app.model.SearchTemplate;
import androidx.car.app.model.Template;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class SearchScreen extends Screen {

    public enum SEARCHCONTEXT {
        SEARCH_LISTS, SEARCH_TRACKS
    }

    private final SEARCHCONTEXT searchContext;
    SearchTemplate.Builder searchBuilder;
    private ItemList itemList = null;
    private final Object lock = new Object();
    private boolean isLoading;

    SearchScreen(final CarContext carContext, final SEARCHCONTEXT searchContext) {
        super(carContext);
        this.searchContext = searchContext;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {

        final SearchTemplate.SearchCallback searchCallback = new SearchTemplate.SearchCallback() {
            @Override
            public void onSearchTextChanged(@NonNull final String searchText) {
                doSearch(searchText);
                invalidate();
            }
        };

        if (searchBuilder == null) {
            doSearch("");
        }
        searchBuilder = new SearchTemplate.Builder(searchCallback)
                .setShowKeyboardByDefault(true)
                .setItemList(isLoading ? new ItemList.Builder().build() : itemList)
                .setLoading(isLoading)
                .setHeaderAction(Action.BACK);
        return searchBuilder.build();
    }

    private void doSearch(final String searchText) {
        synchronized (lock) {
            if (isLoading) {
                return;
            }
            isLoading = true;
        }

        AndroidRxUtils.computationScheduler.scheduleDirect(() -> {
            final ItemList.Builder itemListBuilder = new ItemList.Builder();
            if (searchContext == SEARCHCONTEXT.SEARCH_LISTS) {
                final List<StoredList> storedLists = DataStore.getLists();
                for (StoredList list : storedLists) {
                    if (StringUtils.containsIgnoreCase(list.title, searchText) && list.getNumberOfCaches() > 0) {
                        itemListBuilder.addItem(buildPOIItem(new Geopoint(0, 0), list.title, list.getTitleAndCount()));
                    }
                }
            } else if (searchContext == SEARCHCONTEXT.SEARCH_TRACKS) {
                final List<Trackfiles> tracks = Trackfiles.getTrackfiles();
                for (Trackfiles track : tracks) {
                    itemListBuilder.addItem(buildPOIItem(new Geopoint(0, 0), track.getDisplayname(), ""));
                }
            }
            itemList = itemListBuilder.build();
            invalidate();
            synchronized (lock) {
                isLoading = false;
            }
        });
    }

    private Row buildPOIItem(final Geopoint geopoint, final String name, final String additionalInfo) {
        final Place place = new Place.Builder(geopoint != null ? CarLocation.create(geopoint.getLongitude(), geopoint.getLatitude()) : CarLocation.create(0, 0))
                .setMarker(new PlaceMarker.Builder().setColor(CarColor.createCustom(0xFF543a20, 0xFF543a20)).build())
                .build();
        final Metadata metadata = new Metadata.Builder().setPlace(place).build();

        return new Row.Builder()
                .setTitle(name)
                .setMetadata(metadata)
                .setBrowsable(true)
                .setOnClickListener(() -> Log.e("'" + name + "' selected"))
                .addText(additionalInfo)
                .build();
    }

}
