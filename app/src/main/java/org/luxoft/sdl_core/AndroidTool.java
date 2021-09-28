package org.luxoft.sdl_core;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

public class AndroidTool {

    /**
     * Service needs to be foregrounded in Android O and above.
     * This will prevent apps in the background from crashing when they try to start SdlService,
     * because Android O doesn't allow background apps to start background services.
     *
     * @param context   The object to use to start service.
     * @param intent    The object to identify the service to be started. The Intent must be fully explicit.
     * */
    public static void startService(@NonNull Context context, @NonNull Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

}
