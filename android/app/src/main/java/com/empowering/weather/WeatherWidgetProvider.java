package com.empowering.weather;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.location.Location;
import android.location.LocationManager;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
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
            views.setTextViewText(R.id.txtLocation, "Prec: -- | Hum: -- | UV: --");
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
                double lat = 0;
                double lon = 0;
                boolean hasLocation = false;
                double[] nativeLoc = tryGetLastKnownLocation(context);
                if (nativeLoc != null) {
                    lat = nativeLoc[0];
                    lon = nativeLoc[1];
                    hasLocation = !(lat == 0 && lon == 0);
                }
                if (!hasLocation) {
                    WidgetData data = new WidgetData("Empowering Weather", "--°C", "Prec: -- | Hum: -- | UV: --", "Open app to grant location");
                    updateAppWidgetOpenApp(context, appWidgetManager, appWidgetId, data);
                    return;
                }
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
                    JSONObject cur = weather != null ? weather.optJSONObject("current") : null;
                    String quip = root.optString("weather_quip", "");
                    String temp = cur != null ? (cur.opt("temp_c") + "°C") : "--°C";
                    String details = buildDetailsFromCurrent(cur);
                    WidgetData data = new WidgetData(quip.isEmpty() ? "Empowering Weather" : quip, temp, details, "Tap to refresh");
                    updateAppWidget(context, appWidgetManager, appWidgetId, data);
                } else {
                    String st = hasLocation ? ("Error " + code) : "Open app to set location";
                    WidgetData data = new WidgetData("Empowering Weather", "--°C", "Prec: -- | Hum: -- | UV: --", st);
                    if (hasLocation) {
                        updateAppWidget(context, appWidgetManager, appWidgetId, data);
                    } else {
                        updateAppWidgetOpenApp(context, appWidgetManager, appWidgetId, data);
                    }
                }
            } catch (Exception e) {
                WidgetData data = new WidgetData("Empowering Weather", "--°C", "Prec: -- | Hum: -- | UV: --", "Offline");
                updateAppWidget(context, appWidgetManager, appWidgetId, data);
            }
        }).start();
    }

    // Open app when user taps the widget (used when location missing/permissions needed)
    private static void updateAppWidgetOpenApp(Context context, AppWidgetManager appWidgetManager, int appWidgetId, WidgetData data) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget);
        if (data == null) {
            views.setTextViewText(R.id.txtQuip, "Empowering Weather");
            views.setTextViewText(R.id.txtTemp, "--°C");
            views.setTextViewText(R.id.txtLocation, "Prec: -- | Hum: -- | UV: --");
            views.setTextViewText(R.id.txtStatus, "Open app to set location");
        } else {
            views.setTextViewText(R.id.txtQuip, data.quip);
            views.setTextViewText(R.id.txtTemp, data.temp);
            views.setTextViewText(R.id.txtLocation, data.location);
            views.setTextViewText(R.id.txtStatus, data.status);
        }
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        views.setOnClickPendingIntent(R.id.widget_root, pi);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static String buildDetailsFromCurrent(JSONObject cur) {
        try {
            if (cur == null) return "Prec: -- | Hum: -- | UV: --";
            // Humidity
            String humStr = "--";
            if (cur.has("humidity") && !cur.isNull("humidity")) {
                humStr = String.valueOf(cur.optInt("humidity"));
                if (!"--".equals(humStr)) humStr += "%";
            }

            // UV
            String uvStr = "--";
            if (cur.has("uv") && !cur.isNull("uv")) {
                Object uv = cur.opt("uv");
                uvStr = String.valueOf(uv);
            }

            // Precipitation: prefer percent fields, fall back to precip_mm
            String precStr = extractRainChancePercent(cur);
            if (precStr == null) {
                if (cur.has("precip_mm") && !cur.isNull("precip_mm")) {
                    precStr = cur.opt("precip_mm") + " mm";
                } else {
                    precStr = "--";
                }
            }

            return "Prec: " + precStr + " | Hum: " + humStr + " | UV: " + uvStr;
        } catch (Exception e) {
            return "Prec: -- | Hum: -- | UV: --";
        }
    }

    private static String extractRainChancePercent(JSONObject h) {
        try {
            if (h == null) return null;
            // Try a set of common keys used across APIs
            if (h.has("daily_chance_of_rain") && !h.isNull("daily_chance_of_rain"))
                return h.optInt("daily_chance_of_rain") + "%";
            if (h.has("chance_of_rain") && !h.isNull("chance_of_rain"))
                return h.optInt("chance_of_rain") + "%";
            if (h.has("chanceofrain") && !h.isNull("chanceofrain"))
                return h.optInt("chanceofrain") + "%";
            if (h.has("pop") && !h.isNull("pop")) return h.optInt("pop") + "%";
            if (h.has("will_it_rain") && !h.isNull("will_it_rain"))
                return (h.optInt("will_it_rain") != 0 ? 100 : 0) + "%";
        } catch (Exception ignored) {}
        return null;
    }

    private static double[] tryGetLastKnownLocation(Context context) {
        try {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null; // No permission granted yet
            }
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            Location best = null;
            try {
                Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (gps != null) best = gps;
            } catch (Throwable ignored) {}
            try {
                Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (best == null || (net != null && net.getTime() > best.getTime())) best = net;
            } catch (Throwable ignored) {}
            if (best != null) {
                return new double[]{ best.getLatitude(), best.getLongitude() };
            }
        } catch (Throwable ignored) {}
        return null;
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
