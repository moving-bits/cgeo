package cgeo.geocaching.connector.su;

import cgeo.geocaching.connector.AbstractLoggingManager;
import cgeo.geocaching.connector.ImageResult;
import cgeo.geocaching.connector.LogResult;
import cgeo.geocaching.enumerations.Loaders;
import cgeo.geocaching.enumerations.StatusCode;
import cgeo.geocaching.log.LogCacheActivity;
import cgeo.geocaching.log.LogType;
import cgeo.geocaching.log.ReportProblemType;
import cgeo.geocaching.log.TrackableLog;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.models.Image;
import cgeo.geocaching.utils.Log;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class SuLoggingManager extends AbstractLoggingManager implements LoaderManager.LoaderCallbacks<String> {

    @NonNull
    private final SuConnector connector;
    @NonNull
    private final Geocache cache;
    @NonNull
    private final LogCacheActivity activity;

    SuLoggingManager(@NonNull final LogCacheActivity activity, @NonNull final SuConnector connector, @NonNull final Geocache cache) {
        this.connector = connector;
        this.cache = cache;
        this.activity = activity;
    }

    @Override
    public void init() {
        activity.getSupportLoaderManager().initLoader(Loaders.LOGGING_GEOCHACHING.getLoaderId(), null, this);
    }

    @Override
    @NonNull
    public final LogResult postLog(@NonNull final LogType logType, @NonNull final Calendar date, @NonNull final String log, @Nullable final String logPassword, @NonNull final List<TrackableLog> trackableLogs, @NonNull final ReportProblemType reportProblem) {
        try {
            return SuApi.postLog(cache, logType, date, log);
        } catch (final SuApi.SuApiException e) {
            Log.e("Logging manager SuApi.postLog exception: ", e);
            return new LogResult(StatusCode.LOG_POST_ERROR, "");
        }
    }

    @Override
    @NonNull
    public final ImageResult postLogImage(final String logId, final Image image) {
        return SuApi.postImage(cache, image);
    }

    @Override
    @NonNull
    public List<LogType> getPossibleLogTypes() {
        return connector.getPossibleLogTypes(cache);
    }

    @NonNull
    @Override
    public List<ReportProblemType> getReportProblemTypes(@NonNull final Geocache geocache) {
        return Collections.emptyList();
    }

    @Override
    public Loader<String> onCreateLoader(final int id, final Bundle args) {
        activity.onLoadStarted();
        return new SuLoggingLoader(activity.getBaseContext());
    }

    @Override
    public void onLoadFinished(final Loader<String> loader, final String data) {
        activity.onLoadFinished();
    }

    @Override
    public void onLoaderReset(final Loader<String> loader) {
        // nothing to do
    }

    static class SuLoggingLoader extends AsyncTaskLoader<String> {
        SuLoggingLoader(final Context context) {
            super(context);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public String loadInBackground() {
            // XXX: We need this dummy Loader because in other case button "Send log" will complain
            // about "Downloading data in progress"
            return "nothing to load here";
        }
    }
}
