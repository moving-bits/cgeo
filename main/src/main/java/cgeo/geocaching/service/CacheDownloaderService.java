package cgeo.geocaching.service;

import cgeo.geocaching.R;
import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.list.StoredList;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.ui.ViewUtils;
import cgeo.geocaching.ui.dialog.Dialogs;
import cgeo.geocaching.utils.LocalizationUtils;
import cgeo.geocaching.utils.Log;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioGroup;

import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Entry point for starting background cache downloads.
 * <p>
 * Despite the historical {@code Service} suffix, this class is no longer an Android service:
 * the actual download work runs in {@link CacheDownloaderWorker} via {@link WorkManager}.
 * The public static API is preserved for callers throughout the app.
 */
public final class CacheDownloaderService {

    private static volatile boolean shouldStop = false;
    static final Map<String, DownloadTaskProperties> downloadQuery = new HashMap<>();

    private CacheDownloaderService() {
        // no instances
    }

    public static boolean isDownloadPending(final String geocode) {
        return downloadQuery.containsKey(geocode);
    }

    public static boolean isDownloadPending(final Geocache geocache) {
        return isDownloadPending(geocache.getGeocode());
    }

    public static void downloadCaches(final Activity context, final Collection<String> geocodes, final boolean defaultForceRedownload, final boolean isOffline, @Nullable final Runnable onStartCallback) {
        if (geocodes.isEmpty()) {
            ActivityMixin.showToast(context, LocalizationUtils.getString(R.string.warn_save_nothing));
            return;
        }
        if (isOffline) {
            downloadCachesInternal(context, geocodes, null, false, defaultForceRedownload, onStartCallback);
            return;
        }
        if (DataStore.getUnsavedGeocodes(geocodes).size() == geocodes.size()) {
            askForListsIfNecessaryAndDownload(context, geocodes, false, false, false, onStartCallback);
            return;
        }

        // some caches are already stored offline, thus show the advanced selection dialog

        final View content = LayoutInflater.from(context).inflate(R.layout.dialog_background_download_config, null);
        final RadioGroup radioGroup = content.findViewById(R.id.radioGroup);

        Dialogs.newBuilder(context)
                .setView(content)
                .setTitle(R.string.caches_store_background_title)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    final int id = radioGroup.getCheckedRadioButtonId();
                    if (id == R.id.radio_button_refresh_and_add) {
                        askForListsIfNecessaryAndDownload(context, geocodes, false, true, false, onStartCallback);
                    } else if (id == R.id.radio_button_refresh_and_keep) {
                        askForListsIfNecessaryAndDownload(context, geocodes, true, true, false, onStartCallback);
                    } else if (id == R.id.radio_button_add_to_list) {
                        askForListsIfNecessaryAndDownload(context, geocodes, false, false, false, onStartCallback);
                    } else {
                        askForListsIfNecessaryAndDownload(context, DataStore.getUnsavedGeocodes(geocodes), false, false, false, onStartCallback);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();

    }

    public static void storeCache(final Activity context, final Geocache cache, final boolean fastStoreOnLastSelection, @Nullable final Runnable onStartCallback) {
        if (Settings.getChooseList() || cache.isOffline()) {
            // let user select list to store cache in
            new StoredList.UserInterface(context).promptForMultiListSelection(R.string.lists_title, selectedListIds -> downloadCachesInternal(context, Collections.singleton(cache.getGeocode()), selectedListIds, false, true, onStartCallback), true, cache.getLists(), fastStoreOnLastSelection);
        } else {
            downloadCachesInternal(context, Collections.singleton(cache.getGeocode()), Collections.singleton(StoredList.STANDARD_LIST_ID), false, true, onStartCallback);
        }
    }

    public static void refreshCache(final Activity context, final String geocode, final boolean isOffline, @Nullable final Runnable onStartCallback) {
        askForListsIfNecessaryAndDownload(context, Collections.singleton(geocode), isOffline, true, isOffline, onStartCallback);
    }

    private static void askForListsIfNecessaryAndDownload(final Activity context, final Collection<String> geocodes, final boolean keepExistingLists, final boolean forceRedownload, final boolean isOffline, @Nullable final Runnable onStartCallback) {
        if (isOffline) {
            downloadCachesInternal(context, geocodes, null, keepExistingLists, forceRedownload, onStartCallback);
        } else if (Settings.getChooseList()) {
            // let user select list to store cache in
            new StoredList.UserInterface(context).promptForMultiListSelection(keepExistingLists ? R.string.lists_title_new_caches : R.string.lists_title, selectedListIds -> downloadCachesInternal(context, geocodes, selectedListIds, keepExistingLists, forceRedownload, onStartCallback), true, Collections.emptySet(), false);
        } else {
            downloadCachesInternal(context, geocodes, Collections.singleton(StoredList.STANDARD_LIST_ID), keepExistingLists, forceRedownload, onStartCallback);
        }
    }

    private static void downloadCachesInternal(final Activity context, final Collection<String> geocodes, @Nullable final Set<Integer> listIds, final boolean keepExistingLists, final boolean forceRedownload, @Nullable final Runnable onStartCallback) {

        final ArrayList<String> newGeocodes = new ArrayList<>();

        for (String geocode : geocodes) {
            final DownloadTaskProperties properties = new DownloadTaskProperties(listIds, keepExistingLists, forceRedownload);
            final boolean isNewGeocode;
            synchronized (downloadQuery) {
                isNewGeocode = downloadQuery.get(geocode) == null;
                properties.merge(downloadQuery.get(geocode));
                downloadQuery.put(geocode, properties);
            }
            if (isNewGeocode) {
                newGeocodes.add(geocode);
            }
        }

        if (newGeocodes.isEmpty()) {
            return;
        }

        Log.d("DOWNLOAD: " + newGeocodes);

        // a fresh user-initiated batch clears a previous stop request; subsequent batches enqueued
        // before this point share that reset (they were enqueued while shouldStop was still false anyway).
        shouldStop = false;

        final Data inputData = new Data.Builder()
                .putStringArray(CacheDownloaderWorker.EXTRA_GEOCODES, newGeocodes.toArray(new String[0]))
                .build();
        final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(CacheDownloaderWorker.class)
                .setInputData(inputData)
                .build();
        // APPEND_OR_REPLACE keeps batches in a single queue so they're processed sequentially,
        // matching the previous IntentService behaviour.
        WorkManager.getInstance(context.getApplicationContext())
                .beginUniqueWork(CacheDownloaderWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
                .enqueue();
        ViewUtils.showToast(context, R.string.download_started);

        if (onStartCallback != null) {
            onStartCallback.run();
        }
    }

    public static void requestStopService() {
        shouldStop = true;
    }

    static boolean isStopRequested() {
        return shouldStop;
    }

    static class DownloadTaskProperties {
        final Set<Integer> listIds = new HashSet<>();
        boolean forceDownload;
        boolean keepExistingLists;

        private DownloadTaskProperties(@Nullable final Set<Integer> listIds, final boolean keepExistingLists, final boolean forceDownload) {
            if (listIds != null) {
                this.listIds.addAll(listIds);
            }
            this.keepExistingLists = keepExistingLists;
            this.forceDownload = forceDownload;
        }

        public DownloadTaskProperties merge(@Nullable final DownloadTaskProperties additionalProperties) {
            if (additionalProperties != null) {
                this.listIds.addAll(additionalProperties.listIds);
                this.keepExistingLists |= additionalProperties.keepExistingLists;
                this.forceDownload |= additionalProperties.forceDownload;
            }
            return this;
        }
    }
}
