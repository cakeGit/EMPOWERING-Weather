package com.empowering.weather;

import android.content.pm.PackageManager;
import android.Manifest;
import androidx.core.content.ContextCompat;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

/**
 * Foreground service that registers for location updates using a PendingIntent so updates survive
 * process death and can be restarted from BOOT_COMPLETED.
 */
public class LocationUpdatesService extends Service {
    private static final String TAG = "LocUpdatesService";
    // no notification channel: service runs without startForeground
    private boolean mForegroundStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        // record service starting in prefs for visibility
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("location_service_running", true).putLong("location_service_started_at", System.currentTimeMillis()).apply();
        } catch (Throwable ignored) {}
    // Do not create a notification or call startForeground here.
    // On modern Android starting a location-type foreground service requires
    // special permissions (android.permission.FOREGROUND_SERVICE_LOCATION).
    // Instead, only request location updates when runtime location permission
    // is available to avoid SecurityException.

        try {
            // Check runtime location permission before requesting updates
            boolean hasLocationPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (!hasLocationPerm) {
                Log.w(TAG, "missing location permission; not requesting updates");
                return;
            }
            LocationRequest req = LocationRequest.create();
            req.setInterval(30_000);
            req.setFastestInterval(10_000);
            req.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

            Intent intent = new Intent(this, LocationBroadcastReceiver.class);
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, flags);

            LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(req, pi);
            Log.i(TAG, "requested location updates via PendingIntent");
        } catch (SecurityException se) {
            Log.w(TAG, "missing location permission", se);
        } catch (Throwable t) {
            Log.w(TAG, "failed to request updates", t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Keep running until explicitly stopped
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("location_service_running", false).putLong("location_service_stopped_at", System.currentTimeMillis()).apply();
        } catch (Throwable ignored) {}
        try {
            Intent intent = new Intent(this, LocationBroadcastReceiver.class);
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, flags);
            LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(pi);
        } catch (Throwable ignored) {}
        if (mForegroundStarted) {
            try { stopForeground(true); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
