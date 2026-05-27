package cgeo.geocaching.service;

import cgeo.geocaching.ui.notifications.Notifications;
import cgeo.geocaching.utils.Log;

import android.app.IntentService;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public abstract class AbstractForegroundIntentService extends IntentService {
    protected static String logTag = "ForegroundIntentService";

    protected final int wakelockTimeout = 10 * 60 * 1000;

    protected NotificationCompat.Builder notification;
    protected NotificationManagerCompat notificationManager;
    private PowerManager.WakeLock wakeLock;

    public AbstractForegroundIntentService() {
        super(logTag);
        // Do NOT enable intent redelivery: a system-scheduled restart of a foreground service
        // from the background can fail on Android 12+ with ForegroundServiceStartNotAllowedException,
        // which manifests as "Unable to create service ...". Subclasses also typically rely on
        // in-memory state that is lost when the process dies, so a blind redelivery wouldn't
        // resume work correctly anyway.
        setIntentRedelivery(false);
    }

    protected abstract NotificationCompat.Builder createInitialNotification();

    protected abstract int getForegroundNotificationId();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(logTag + ".onCreate");

        final PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cgeo:" + logTag);
        wakeLock.acquire(wakelockTimeout); // set timeout in case something got really wrong. Will be released earlier if work is done.
        Log.w(logTag + " - WakeLock acquired");

        notificationManager = Notifications.getNotificationManager(this);
        notification = createInitialNotification()
                .setOnlyAlertOnce(true)
                .setSilent(true);

        try {
            startForeground(getForegroundNotificationId(), notification.build());
        } catch (Exception e) {
            // On Android 12+ startForeground() can throw ForegroundServiceStartNotAllowedException
            // when invoked from a background context (e.g. system-triggered restart). Bail out
            // cleanly instead of letting ActivityThread wrap this into a fatal
            // "Unable to create service" RuntimeException.
            Log.e(logTag + " - startForeground failed, stopping service", e);
            stopSelf();
        }
    }


    @Override
    public void onDestroy() {
        Log.v(logTag + ".onDestroy");
        wakeLock.release();
        Log.w(logTag + " - WakeLock released");
        super.onDestroy();
    }

    protected void updateForegroundNotification() {
        Notifications.send(this, getForegroundNotificationId(), notification);
    }
}
