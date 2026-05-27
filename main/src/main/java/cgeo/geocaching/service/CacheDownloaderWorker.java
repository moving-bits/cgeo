package cgeo.geocaching.service;

import cgeo.geocaching.R;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.ui.notifications.NotificationChannels;
import cgeo.geocaching.ui.notifications.Notifications;
import cgeo.geocaching.utils.AndroidRxUtils;
import cgeo.geocaching.utils.LocalizationUtils;
import cgeo.geocaching.utils.Log;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.functions.Function;

public class CacheDownloaderWorker extends Worker {

    static final String UNIQUE_WORK_NAME = "cgeo_cache_downloader";
    static final String EXTRA_GEOCODES = "extra_geocodes";

    private NotificationCompat.Builder notification;
    private NotificationManagerCompat notificationManager;
    private final AtomicInteger cachesDownloaded = new AtomicInteger();
    private int totalCaches;

    public CacheDownloaderWorker(@NonNull final Context context, @NonNull final WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        final Context context = getApplicationContext();
        final String[] geocodeArray = getInputData().getStringArray(EXTRA_GEOCODES);
        if (geocodeArray == null || geocodeArray.length == 0) {
            return Result.success();
        }
        totalCaches = geocodeArray.length;

        notificationManager = Notifications.getNotificationManager(context);
        notification = createProgressNotification(context);
        Notifications.send(context, getProgressNotificationId(), notification);

        Log.d("Download task started");
        try {
            Observable.fromArray(geocodeArray)
                    .flatMap((Function<String, Observable<String>>) geocode -> Observable.create((ObservableOnSubscribe<String>) emitter -> {
                        handleDownload(geocode);
                        emitter.onComplete();
                    }).subscribeOn(AndroidRxUtils.refreshScheduler))
                    .blockingSubscribe();

            Log.d("Download task completed");

            final int success = cachesDownloaded.get();
            final int failed = totalCaches - success;
            if (failed > 0) {
                final boolean stopped = CacheDownloaderService.isStopRequested() || isStopped();
                showEndNotification(context, LocalizationUtils.getString(
                        stopped ? R.string.caches_store_background_result_canceled : R.string.caches_store_background_result_failed,
                        success, totalCaches));
            } else if (success != 1) { // see #15881
                showEndNotification(context, LocalizationUtils.getPlural(R.plurals.caches_store_background_result, success));
            }
            return Result.success();
        } finally {
            notificationManager.cancel(getProgressNotificationId());
        }
    }

    private void handleDownload(final String geocode) {
        try {
            if (CacheDownloaderService.isStopRequested() || isStopped()) {
                Log.i("download canceled");
                return;
            }

            Log.d("Download #" + cachesDownloaded.get() + " " + geocode + " started");

            final CacheDownloaderService.DownloadTaskProperties properties;
            synchronized (CacheDownloaderService.downloadQuery) {
                properties = CacheDownloaderService.downloadQuery.put(geocode, null); // set to null to mark "in progress"
            }
            if (properties == null) {
                throw new IllegalStateException("The cache is not present in the download query");
            }

            notification.setProgress(totalCaches, cachesDownloaded.get(), false);
            notification.setContentText(cachesDownloaded.get() + "/" + totalCaches);
            Notifications.send(getApplicationContext(), getProgressNotificationId(), notification);

            // merge current lists and additional lists
            final Set<Integer> combinedListIds = new HashSet<>(properties.listIds);
            final Geocache cache = DataStore.loadCache(geocode, LoadFlags.LOAD_CACHE_OR_DB);
            if (cache != null && !cache.getLists().isEmpty()) {
                if (properties.keepExistingLists) {
                    combinedListIds.clear();
                }
                combinedListIds.addAll(cache.getLists());
            }

            if (Geocache.storeCache(null, geocode, combinedListIds, properties.forceDownload, null)) {
                GeocacheChangedBroadcastReceiver.sendBroadcast(getApplicationContext(), geocode);
                synchronized (CacheDownloaderService.downloadQuery) {
                    if (CacheDownloaderService.downloadQuery.get(geocode) == null) {
                        CacheDownloaderService.downloadQuery.remove(geocode);
                    }
                }
                Log.d("Download #" + cachesDownloaded.get() + " " + geocode + " completed");
                cachesDownloaded.incrementAndGet();
            } else {
                Log.d("Download #" + cachesDownloaded.get() + " " + geocode + " failed");
            }
        } catch (Exception ex) {
            Log.e("exception while background download", ex);
        }
    }

    private int getProgressNotificationId() {
        return Notifications.ID_FOREGROUND_NOTIFICATION_CACHES_DOWNLOADER;
    }

    private NotificationCompat.Builder createProgressNotification(final Context context) {
        final PendingIntent actionCancelIntent = PendingIntent.getBroadcast(context, 0,
                new Intent(context, StopCacheDownloadServiceReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return Notifications.createNotification(context, NotificationChannels.FOREGROUND_SERVICE_NOTIFICATION, R.string.caches_store_background_title)
                .setProgress(100, 0, true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .addAction(R.drawable.ic_menu_cancel, LocalizationUtils.getString(android.R.string.cancel), actionCancelIntent);
    }

    private void showEndNotification(final Context context, final String text) {
        Notifications.send(context, Settings.getUniqueNotificationId(), Notifications.createTextContentNotification(
                context, NotificationChannels.CACHES_DOWNLOADED_NOTIFICATION, R.string.caches_store_background_title, text).setSilent(true));
    }
}
