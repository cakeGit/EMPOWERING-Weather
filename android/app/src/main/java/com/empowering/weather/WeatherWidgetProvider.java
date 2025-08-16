package com.empowering.weather;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class WeatherWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.empowering.weather.REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, null);
            fetchAndUpdate(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, WeatherWidgetProvider.class));
            for (int id : ids) {
                fetchAndUpdate(context, mgr, id);
            }
        }
    }

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, WidgetData data) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget);
        if (data == null) {
            views.setTextViewText(R.id.txtQuip, "Empowering Weather");
            views.setTextViewText(R.id.txtTemp, "--°C");
            views.setTextViewText(R.id.txtLocation, "Location: --");
            views.setTextViewText(R.id.txtStatus, "Updating…");
        } else {
            views.setTextViewText(R.id.txtQuip, data.quip);
            views.setTextViewText(R.id.txtTemp, data.temp);
            views.setTextViewText(R.id.txtLocation, data.location);
            views.setTextViewText(R.id.txtStatus, data.status);
        }

        Intent refreshIntent = new Intent(context, WeatherWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        views.setOnClickPendingIntent(R.id.widget_root, pi);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void fetchAndUpdate(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        new Thread(() -> {
            try {
                // Attempt to hit the same server the app uses. If CAP_SERVER_URL is set during packaging,
                // you can bake it into the manifest metadata or hardcode it here if needed.
                String server = "https://weather.oreostack.uk";
                // Capacitor Preferences store values as strings in SharedPreferences named: <appId>_CapacitorStorage
                // Keys are the provided keys; values stored under `value`. To keep it simple, read by our own namespace too.
                double lat = 0;
                double lon = 0;
                try {
                    String prefName = context.getPackageName() + "_CapacitorStorage";
                    String latStr = context.getSharedPreferences(prefName, Context.MODE_PRIVATE).getString("lat", null);
                    String lonStr = context.getSharedPreferences(prefName, Context.MODE_PRIVATE).getString("lon", null);
                    if (latStr != null) lat = Double.parseDouble(latStr);
                    if (lonStr != null) lon = Double.parseDouble(lonStr);
                } catch (Exception ignored) {}
        boolean hasLocation = !(lat == 0 && lon == 0);
                URL url = new URL(server + "/api?lat=" + lat + "&lon=" + lon);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()
                ));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                if (code >= 200 && code < 300) {
                    JSONObject root = new JSONObject(sb.toString());
                    JSONObject weather = root.optJSONObject("weather");
                    JSONObject loc = weather != null ? weather.optJSONObject("location") : null;
                    JSONObject cur = weather != null ? weather.optJSONObject("current") : null;
                    String name = loc != null ? loc.optString("name", "—") : "—";
                    String region = loc != null ? loc.optString("region", "") : "";
                    String location = "Location: " + name + (region.isEmpty() ? "" : ", " + region);
                    String quip = root.optString("weather_quip", "");
                    String temp = cur != null ? (cur.opt("temp_c") + "°C") : "--°C";
                    WidgetData data = new WidgetData(quip.isEmpty() ? "Empowering Weather" : quip, temp, location, "Tap to refresh");
                    updateAppWidget(context, appWidgetManager, appWidgetId, data);
                } else {
                    String st = hasLocation ? ("Error " + code) : "Open app to set location";
                    WidgetData data = new WidgetData("Empowering Weather", "--°C", "Location: --", st);
                    updateAppWidget(context, appWidgetManager, appWidgetId, data);
                }
            } catch (Exception e) {
                WidgetData data = new WidgetData("Empowering Weather", "--°C", "Location: --", "Offline");
                updateAppWidget(context, appWidgetManager, appWidgetId, data);
            }
        }).start();
    }

    private static class WidgetData {
        String quip;
        String temp;
        String location;
        String status;
        WidgetData(String quip, String temp, String location, String status) {
            this.quip = quip;
            this.temp = temp;
            this.location = location;
            this.status = status;
        }
    }
}
