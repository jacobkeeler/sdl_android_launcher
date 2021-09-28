package org.luxoft.sdl_core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class SdlLauncherService extends Service {

    public final static String TAG = SdlLauncherService.class.getSimpleName();

    public final static String ACTION_SDL_SERVICE_START = "ACTION_SDL_SERVICE_START";
    public final static String ACTION_SDL_SERVICE_STOP = "ACTION_SDL_SERVICE_STOP";
    public final static String ON_SDL_SERVICE_STOPPED = "org.luxoft.sdl_core.ON_SDL_SERVICE_STOPPED";
    public final static String ON_SDL_SERVICE_STARTED = "org.luxoft.sdl_core.ON_SDL_SERVICE_STARTED";

    private static final String APP_ID = "sdl_service";
    private static final String SERVICE_NAME = "SdlLauncherService";
    private static final String SDL_CONTENT_TITLE = "SDL Android";

    private static final int FOREGROUND_SERVICE_ID = 123;
    private static final int SDL_STARTED_DELAY = 2000;
    private static final int SDL_JOIN_WAIT = 5000;

    private String channel_id = null;
    private boolean is_first_load_ = true;
    private Thread sdl_thread_ = null;

    private native static void StartSDLNative();
    private native static void StopSDLNative();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action.equals(ACTION_SDL_SERVICE_START)) {
            if (startSdlThread()) {
                enterForeground();
            }
            return START_NOT_STICKY;
        }

        if (action.equals(ACTION_SDL_SERVICE_STOP)) {
            stopSdlThread();
            exitForeground();
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private boolean startSdlThread() {
        if (is_first_load_) {
            try {
                System.loadLibrary("c++_shared");
                System.loadLibrary("emhashmap");
                System.loadLibrary("bson");
                System.loadLibrary("boost_system");
                System.loadLibrary("boost_regex");
                System.loadLibrary("boost_thread");
                System.loadLibrary("boost_date_time");
                System.loadLibrary("boost_filesystem");
                System.loadLibrary("smartDeviceLinkCore");
                is_first_load_ = false;
            } catch (UnsatisfiedLinkError e) {
                showToastMessage("Failed to load the library: " + e.getMessage());
                return false;
            }
        }

        sdl_thread_ = new Thread() {
            @Override
            public void run() {
                StartSDLNative();
            }
        };
        sdl_thread_.start();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                final Intent start_intent = new Intent(ON_SDL_SERVICE_STARTED);
                getApplicationContext().sendBroadcast(start_intent);
                updateServiceNotification("SDL core is running");
            }
        }, SDL_STARTED_DELAY);

        return true;
    }

    private void stopSdlThread() {
        updateServiceNotification("SDL core is stopping...");

        try {
            StopSDLNative();
            if(sdl_thread_.isAlive()) {
                sdl_thread_.join(SDL_JOIN_WAIT);
            }
            if (sdl_thread_.isAlive()) {
                sdl_thread_.interrupt();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        final Intent intent = new Intent(ON_SDL_SERVICE_STOPPED);
        getApplicationContext().sendBroadcast(intent);
    }

    private void showToastMessage(String message) {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_LONG;

        Toast toast = Toast.makeText(context, message, duration);
        toast.show();
    }

    private void updateServiceNotification(String text) {
        Notification notification = getServiceNotification(text);
        if (notification == null) {
            Log.w(TAG, "Cannot update notification");
            return;
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(FOREGROUND_SERVICE_ID, notification);
    }

    private Notification getServiceNotification(String text){
        // The PendingIntent to launch our activity if the user selects this notification
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (channel_id == null) {
                Log.w(TAG, "Cannot create notification. Create and save channel_id firstly");
                return null;
            }
            return new NotificationCompat.Builder(this, channel_id)
                    .setContentTitle(SDL_CONTENT_TITLE)
                    .setContentText(text)
                    .setOnlyAlertOnce(true) // so when data is updated don't make sound and alert in android 8.0+
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(contentIntent)
                    .build();
        } else {
            return new NotificationCompat.Builder(this)
                    .setContentTitle(SDL_CONTENT_TITLE)
                    .setContentText(text)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(contentIntent)
                    .build();
        }
    }

    private void enterForeground() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Log.w(TAG, "NotificationManager are not available. Notification skipped.");
            return;
        }
        final String text = "SDL core is starting...";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (channel_id == null) {
                NotificationChannel channel = new NotificationChannel(APP_ID, SERVICE_NAME, NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
                channel_id = channel.getId();
            }

            startForeground(FOREGROUND_SERVICE_ID, getServiceNotification(text));
        } else {
            updateServiceNotification(text);
        }
    }

    private void exitForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        } else {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager == null) {
                Log.w(TAG, "NotificationManager are not available. Notification cannot be canceled.");
                return;
            }
            notificationManager.cancel(FOREGROUND_SERVICE_ID);
        }
    }

}
