package com.empowering.weather;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;
import com.google.android.gms.location.LocationResult;
import android.content.SharedPreferences;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;

/**
 * Receives location updates delivered via a PendingIntent from FusedLocationProviderClient.
 * Persists the last location into SharedPreferences and signals the widget to refresh.
 */
public class LocationBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "LocBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            LocationResult result = LocationResult.extractResult(intent);
            if (result == null) return;
            Location loc = result.getLastLocation();
            if (loc == null) return;

            Log.i(TAG, "received location: " + loc.getLatitude() + "," + loc.getLongitude());

            SharedPreferences prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .putBoolean("widget_has_location", true)
                    .putFloat("widget_lat", (float)loc.getLatitude())
                    .putFloat("widget_lon", (float)loc.getLongitude())
                    .putLong("widget_loc_time", System.currentTimeMillis())
                    .apply();

            // Notify the widget provider to refresh now
            Intent refresh = new Intent(context, WeatherWidgetProvider.class);
            refresh.setAction(WeatherWidgetProvider.ACTION_REFRESH);
            context.sendBroadcast(refresh);

            // Also update any AppWidget instances directly to be safe
            try {
                AppWidgetManager mgr = AppWidgetManager.getInstance(context);
                int[] ids = mgr.getAppWidgetIds(new ComponentName(context, WeatherWidgetProvider.class));
                for (int id : ids) {
                    // Trigger an immediate update (the provider will fetch using saved prefs)
                    mgr.updateAppWidget(id, mgr.getAppWidgetInfo(id) != null ? new android.widget.RemoteViews(context.getPackageName(), R.layout.weather_widget) : null);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Log.w(TAG, "onReceive failed", t);
        }
    }
}
