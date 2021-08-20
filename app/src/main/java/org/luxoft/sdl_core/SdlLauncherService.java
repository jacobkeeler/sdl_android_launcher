package org.luxoft.sdl_core;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;

public class SdlLauncherService extends Service {

    public final static String ON_SDL_SERVICE_STOPPED = "ON_SDL_SERVICE_STOPPED";
    public final static String ON_SDL_SERVICE_STARTED = "ON_SDL_SERVICE_STARTED";

    private static final String APP_ID = "sdl_service";
    private static final String SERVICE_NAME = "SdlService";
    private static final String SDL_CONTENT_TITLE = "SDL";

    private static final int FOREGROUND_SERVICE_ID = 123;
    private static final int SDL_STARTED_DELAY = 2000;

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
        enterForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

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
                return super.onStartCommand(intent, flags, startId);
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
            }
        }, SDL_STARTED_DELAY);

        return super.onStartCommand(intent, flags, startId);
    }
    @Override
    public void onDestroy() {
        try {
            StopSDLNative();
            if(sdl_thread_.isAlive()) {
                sdl_thread_.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        final Intent intent = new Intent(ON_SDL_SERVICE_STOPPED);
        getApplicationContext().sendBroadcast(intent);

        super.onDestroy();
    }

    private void showToastMessage(String message) {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_LONG;

        Toast toast = Toast.makeText(context, message, duration);
        toast.show();
    }

    @SuppressLint("NewApi")
    public void enterForeground() {
        NotificationChannel channel = new NotificationChannel(APP_ID, SERVICE_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
            Notification serviceNotification = new Notification.Builder(this, channel.getId())
                    .setContentTitle(SDL_CONTENT_TITLE)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build();
            startForeground(FOREGROUND_SERVICE_ID, serviceNotification);
        }
    }

}
