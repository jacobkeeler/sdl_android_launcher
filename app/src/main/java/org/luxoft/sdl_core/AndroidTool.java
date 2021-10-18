package org.luxoft.sdl_core;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.Comparator;
import java.util.List;

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

    /**
     * Return last valid URI permission grants that have been persisted by the app.
     * @param context context used to retrieve the {@link ContentResolver} content resolver.
     * @return URI permission with write and read granted permissions. Or null if URI permission cannot be found.
     */
    @RequiresApi(29)
    @Nullable
    public static UriPermission getGrantedUriPermission(@NonNull Context context) {
        List<UriPermission> uriPermissions = context.getContentResolver().getPersistedUriPermissions();
        if (uriPermissions.isEmpty()) {
            return null;
        }

        if (uriPermissions.size() == 1) {
            UriPermission permission = uriPermissions.get(0);
            return permission.isReadPermission() && permission.isWritePermission() ? permission : null;
        } else {
            return uriPermissions.stream()
                    .filter(uriPermission -> uriPermission.isWritePermission() && uriPermission.isReadPermission())
                    .max(Comparator.comparingLong(UriPermission::getPersistedTime))
                    .orElse(null);
        }
    }
}
