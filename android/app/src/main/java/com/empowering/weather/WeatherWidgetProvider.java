package com.empowering.weather;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.location.Location;
import android.location.LocationManager;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import android.os.Build;
import android.view.View;
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
            // First, try to display existing data with current age
            long storedTimestamp = getStoredDataTimestamp(context);
            if (storedTimestamp > 0) {
                // We have previous data, show it with current age while we fetch new data
                WidgetData cachedData = createCachedData(context, storedTimestamp);
                if (cachedData != null) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, cachedData);
                }
            } else {
                updateAppWidget(context, appWidgetManager, appWidgetId, null);
            }
            
            // Then fetch and update with fresh data
            fetchAndUpdate(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            // Clear location cache when user taps to refresh
            clearLocationCache(context);
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
            views.setViewVisibility(R.id.txtDataAge, View.GONE);
        } else {
            views.setTextViewText(R.id.txtQuip, data.quip);
            views.setTextViewText(R.id.txtTemp, data.temp);
            views.setTextViewText(R.id.txtLocation, data.location);
            views.setTextViewText(R.id.txtStatus, data.status);
            
            // Show data age
            String ageText = formatDataAge(data.timestamp);
            if (!ageText.isEmpty()) {
                views.setTextViewText(R.id.txtDataAge, ageText);
                views.setViewVisibility(R.id.txtDataAge, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.txtDataAge, View.GONE);
            }
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
                    // Open the native location activity which will request permission and obtain a location
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
                    
                    // Store weather data and timestamp for age tracking
                    storeWeatherData(context, data);
                    
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
            views.setViewVisibility(R.id.txtDataAge, View.GONE);
        } else {
            views.setTextViewText(R.id.txtQuip, data.quip);
            views.setTextViewText(R.id.txtTemp, data.temp);
            views.setTextViewText(R.id.txtLocation, data.location);
            views.setTextViewText(R.id.txtStatus, data.status);
            views.setViewVisibility(R.id.txtDataAge, View.GONE);
        }
    // Launch a small native activity that requests location permission and fetches a location
    Intent openIntent = new Intent(context, NativeLocationActivity.class);
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
            // Check shared preferences for a recently saved native location first
            SharedPreferences prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
            long ts = prefs.getLong("widget_loc_time", 0);
            long now = System.currentTimeMillis();
            // consider a saved location valid for 15 minutes
            if (ts > 0 && (now - ts) < 15L * 60L * 1000L) {
                float lat = prefs.getFloat("widget_lat", 0f);
                float lon = prefs.getFloat("widget_lon", 0f);
                if (!(lat == 0f && lon == 0f)) {
                    return new double[]{ lat, lon };
                }
            }

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

    private static void storeDataTimestamp(Context context, long timestamp) {
        SharedPreferences prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("widget_data_timestamp", timestamp);
        editor.apply();
    }

    private static long getStoredDataTimestamp(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
        return prefs.getLong("widget_data_timestamp", 0);
    }

    private static WidgetData createCachedData(Context context, long timestamp) {
        SharedPreferences prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
        String quip = prefs.getString("widget_quip", "");
        String temp = prefs.getString("widget_temp", "--°C");
        String location = prefs.getString("widget_location", "Prec: -- | Hum: -- | UV: --");
        String status = prefs.getString("widget_status", "Tap to refresh");
        
        if (quip.isEmpty()) quip = "Empowering Weather";
        
        WidgetData data = new WidgetData(quip, temp, location, status);
        data.timestamp = timestamp; // Use stored timestamp for age calculation
        return data;
    }

    private static void storeWeatherData(Context context, WidgetData data) {
        SharedPreferences prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("widget_data_timestamp", data.timestamp);
        editor.putString("widget_quip", data.quip);
        editor.putString("widget_temp", data.temp);
        editor.putString("widget_location", data.location);
        editor.putString("widget_status", data.status);
        editor.apply();
    }

    private static class WidgetData {
        String quip;
        String temp;
        String location;
        String status;
        long timestamp;
        WidgetData(String quip, String temp, String location, String status) {
            this.quip = quip;
            this.temp = temp;
            this.location = location;
            this.status = status;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static void clearLocationCache(Context context) {
        // Clear widget location cache from SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("widget_loc_time");
        editor.remove("widget_lat");
        editor.remove("widget_lon");
        editor.remove("widget_data_timestamp");
        editor.remove("widget_quip");
        editor.remove("widget_temp");
        editor.remove("widget_location");
        editor.remove("widget_status");
        editor.apply();
    }

    private static String formatDataAge(long timestampMs) {
        if (timestampMs <= 0) return "";
        long ageMs = System.currentTimeMillis() - timestampMs;
        long ageSeconds = ageMs / 1000;
        
        if (ageSeconds < 60) {
            return ageSeconds + "s ago";
        } else if (ageSeconds < 3600) {
            long minutes = ageSeconds / 60;
            return minutes + "m ago";
        } else if (ageSeconds < 86400) {
            long hours = ageSeconds / 3600;
            return hours + "h ago";
        } else {
            long days = ageSeconds / 86400;
            return days + "d ago";
        }
    }
}
