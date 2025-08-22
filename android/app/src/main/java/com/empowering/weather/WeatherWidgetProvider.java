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
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.empowering.weather.REFRESH";
    // Single-threaded executor to serialize widget fetch work and avoid spawning many threads
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

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
            views.setTextViewText(R.id.txtQuip, "OverCast");
            views.setTextViewText(R.id.txtTemp, "--°C");
            views.setTextViewText(R.id.txtPrec, "Prec: --");
            views.setTextViewText(R.id.txtHum, "Hum: --");
            views.setTextViewText(R.id.txtUv, "UV: --");
            views.setTextViewText(R.id.txtStatus, "Updating…");
        } else {
            views.setTextViewText(R.id.txtQuip, data.quip);
            views.setTextViewText(R.id.txtTemp, data.temp);
            // data.location is "Prec: X | Hum: Y | UV: Z" -- try to split into parts
            String loc = data.location != null ? data.location : "";
            String prec = "Prec: --";
            String hum = "Hum: --";
            String uv = "UV: --";
            try {
                String[] parts = loc.split("\\|");
                if (parts.length >= 1) prec = parts[0].trim();
                if (parts.length >= 2) hum = parts[1].trim();
                if (parts.length >= 3) uv = parts[2].trim();
            } catch (Throwable ignored) {}
            views.setTextViewText(R.id.txtPrec, prec);
            views.setTextViewText(R.id.txtHum, hum);
            views.setTextViewText(R.id.txtUv, uv);
            views.setTextViewText(R.id.txtStatus, data.status);

            // Set solid color backgrounds and HSL-based text color for each stat
            try {
                int w = dpToPx(context, 80);
                int h = dpToPx(context, 32);

                // Precipitation: grey if 0, else light blue to pure blue
                double precipNorm = parsePrecNorm(partsSafe(loc,0));
                int precipBg = (precipNorm == 0.0) ? 0xFFCCCCCC : lerpColor(0xFFB3E5FC, 0xFF1565C0, precipNorm);
                android.graphics.Bitmap precipBmp = makeSolidBitmap(w, h, precipBg);
                views.setImageViewBitmap(R.id.bg_precip, precipBmp);

                // Humidity: white to blue by percent
                double humNorm = parsePercentNorm(partsSafe(loc,1));
                int humBg = lerpColor(0xFFFFFFFF, 0xFF2196F3, humNorm);
                android.graphics.Bitmap humBmp = makeSolidBitmap(w, h, humBg);
                views.setImageViewBitmap(R.id.bg_humidity, humBmp);

                // UV: green (0-3), yellow (4-6), red (7+)
                double uvVal = parseUvValue(partsSafe(loc,2));
                int uvBg;
                if (uvVal <= 3.0) uvBg = 0xFF66BB6A; // green
                else if (uvVal <= 6.0) uvBg = 0xFFFFEB3B; // yellow
                else uvBg = 0xFFF44336; // red
                android.graphics.Bitmap uvBmp = makeSolidBitmap(w, h, uvBg);
                views.setImageViewBitmap(R.id.bg_uv, uvBmp);

                // Set text color using HSL, 0.6x brightness
                int precipText = hslColor(precipBg, 0.6f);
                int humText = hslColor(humBg, 0.6f);
                int uvText = hslColor(uvBg, 0.6f);
                views.setTextColor(R.id.txtPrec, precipText);
                views.setTextColor(R.id.txtHum, humText);
                views.setTextColor(R.id.txtUv, uvText);
            } catch (Throwable ignored) {}
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
        EXEC.submit(() -> {
            try {
                // Attempt to hit the same server the app uses. If CAP_SERVER_URL is set during packaging,
                // you can bake it into the manifest metadata or hardcode it here if needed.
                String server = "https://weather.oreostack.uk";
                double lat = 0;
                double lon = 0;
                boolean hasLocation = false;

                boolean usingCached = false;
                long savedTs = 0L;
                // Prefer an explicit saved location (from NativeLocationActivity). Use it even if slightly stale
                try {
                    SharedPreferences prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
                    boolean prefHas = prefs.getBoolean("widget_has_location", false);
                    long ts = prefs.getLong("widget_loc_time", 0L);
                    savedTs = ts;
                    if (prefHas && ts > 0) {
                        lat = prefs.getFloat("widget_lat", 0f);
                        lon = prefs.getFloat("widget_lon", 0f);
                        hasLocation = true;
                        // mark as cached if older than 2 minutes (useful indicator)
                        if (System.currentTimeMillis() - ts > 2L * 60L * 1000L) {
                            usingCached = true;
                        }
                    }
                } catch (Throwable ignored) {}

                // If no saved pref location, fall back to last-known providers (if permitted)
                if (!hasLocation) {
                    double[] nativeLoc = tryGetLastKnownLocation(context);
                    if (nativeLoc != null) {
                        lat = nativeLoc[0];
                        lon = nativeLoc[1];
                        hasLocation = true;
                    }
                }

                if (!hasLocation) {
                    WidgetData data = new WidgetData("OverCast", "--°C", "Prec: -- | Hum: -- | UV: --", "Open app to grant location");
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
                    // record fetch time so the widget can show when data was last fetched
                    SharedPreferences prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
                    long fetchTs = System.currentTimeMillis();
                    try { prefs.edit().putLong("widget_last_fetch_time", fetchTs).apply(); } catch (Throwable ignored) {}

                    String status;
                    String fetchedLabel = lastFetchedLabel(fetchTs);
                    if (usingCached) {
                        String age = formatAge(savedTs);
                        status = "Using cached location " + age + " — " + fetchedLabel + " — Tap to refresh";
                    } else {
                        status = fetchedLabel + " — Tap to refresh";
                    }
                    WidgetData data = new WidgetData(quip.isEmpty() ? "OverCast" : quip, temp, details, status);
                    updateAppWidget(context, appWidgetManager, appWidgetId, data);
                } else {
                    String st = hasLocation ? ("Error " + code) : "Open app to set location";
                    if (!hasLocation) st = "Open app to grant location";
                    // append last fetched time if available
                    try {
                        SharedPreferences prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
                        long last = prefs.getLong("widget_last_fetch_time", 0L);
                        if (last > 0) st = st + " — " + lastFetchedLabel(last);
                    } catch (Throwable ignored) {}
                    WidgetData data = new WidgetData("OverCast", "--°C", "Prec: -- | Hum: -- | UV: --", st);
                    if (hasLocation) {
                        updateAppWidget(context, appWidgetManager, appWidgetId, data);
                    } else {
                        updateAppWidgetOpenApp(context, appWidgetManager, appWidgetId, data);
                    }
                }
            } catch (Exception e) {
                String st = "Offline";
                try {
                    SharedPreferences prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
                    long last = prefs.getLong("widget_last_fetch_time", 0L);
                    if (last > 0) st = st + " — " + lastFetchedLabel(last);
                } catch (Throwable ignored) {}
                WidgetData data = new WidgetData("OverCast", "--°C", "Prec: -- | Hum: -- | UV: --", st);
                updateAppWidget(context, appWidgetManager, appWidgetId, data);
            }
        });
    }

    // Open app when user taps the widget (used when location missing/permissions needed)
    private static void updateAppWidgetOpenApp(Context context, AppWidgetManager appWidgetManager, int appWidgetId, WidgetData data) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget);
        if (data == null) {
            views.setTextViewText(R.id.txtQuip, "OverCast");
            views.setTextViewText(R.id.txtTemp, "--°C");
            views.setTextViewText(R.id.txtPrec, "Prec: --");
            views.setTextViewText(R.id.txtHum, "Hum: --");
            views.setTextViewText(R.id.txtUv, "UV: --");
            views.setTextViewText(R.id.txtStatus, "Open app to set location");
        } else {
            views.setTextViewText(R.id.txtQuip, data.quip);
            views.setTextViewText(R.id.txtTemp, data.temp);
            String loc = data.location != null ? data.location : "";
            String prec = "Prec: --";
            String hum = "Hum: --";
            String uv = "UV: --";
            try {
                String[] parts = loc.split("\\|");
                if (parts.length >= 1) prec = parts[0].trim();
                if (parts.length >= 2) hum = parts[1].trim();
                if (parts.length >= 3) uv = parts[2].trim();
            } catch (Throwable ignored) {}
            views.setTextViewText(R.id.txtPrec, prec);
            views.setTextViewText(R.id.txtHum, hum);
            views.setTextViewText(R.id.txtUv, uv);
            views.setTextViewText(R.id.txtStatus, data.status);
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

    // Helpers for widget background gradients and parsing
    private static String partsSafe(String loc, int idx) {
        try {
            if (loc == null) return "";
            String[] p = loc.split("\\|");
            if (idx < p.length) return p[idx].trim();
        } catch (Throwable ignored) {}
        return "";
    }

    private static double parsePrecNorm(String s) {
        try {
            if (s == null) return 0.0;
            // strip non-digits except dot
            java.lang.String t = s.replaceAll("[^0-9.]", "");
            if (t.isEmpty()) return 0.0;
            double v = Double.parseDouble(t);
            // if looks like percent (0-100), convert; else assume mm and normalize against 50mm
            if (s.contains("%")) return Math.min(1.0, v / 100.0);
            return Math.min(1.0, v / 50.0);
        } catch (Throwable ignored) { return 0.0; }
    }

    private static double parsePercentNorm(String s) {
        try {
            if (s == null) return 0.0;
            java.lang.String t = s.replaceAll("[^0-9.]", "");
            if (t.isEmpty()) return 0.0;
            double v = Double.parseDouble(t);
            return Math.min(1.0, v / 100.0);
        } catch (Throwable ignored) { return 0.0; }
    }

    private static double parseUvValue(String s) {
        try {
            if (s == null) return 0.0;
            java.lang.String t = s.replaceAll("[^0-9.]", "");
            if (t.isEmpty()) return 0.0;
            return Double.parseDouble(t);
        } catch (Throwable ignored) { return 0.0; }
    }

    private static int dpToPx(Context ctx, int dp) {
        float scale = ctx.getResources().getDisplayMetrics().density;
        return (int)(dp * scale + 0.5f);
    }

    // Make a solid color bitmap for stat backgrounds
    private static android.graphics.Bitmap makeSolidBitmap(int w, int h, int color) {
        try {
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColor(color);
            paint.setAntiAlias(true);
            canvas.drawRoundRect(0, 0, w, h, h/2f, h/2f, paint);
            return bmp;
        } catch (Throwable t) {
            return android.graphics.Bitmap.createBitmap(1,1, android.graphics.Bitmap.Config.ARGB_8888);
        }
    }

    // Convert ARGB color to HSL, scale brightness, and return ARGB
    private static int hslColor(int argb, float brightnessScale) {
        float[] hsl = new float[3];
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        rgbToHsl(r, g, b, hsl);
        hsl[2] = Math.max(0f, Math.min(1f, hsl[2] * brightnessScale));
        return hslToColor(hsl);
    }

    // Helper: RGB to HSL
    private static void rgbToHsl(int r, int g, int b, float[] hsl) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float h, s, l = (max + min) / 2f;
        if (max == min) {
            h = s = 0f;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == rf) h = (gf - bf) / d + (gf < bf ? 6f : 0f);
            else if (max == gf) h = (bf - rf) / d + 2f;
            else h = (rf - gf) / d + 4f;
            h /= 6f;
        }
        hsl[0] = h;
        hsl[1] = s;
        hsl[2] = l;
    }

    // Helper: HSL to ARGB
    private static int hslToColor(float[] hsl) {
        float h = hsl[0], s = hsl[1], l = hsl[2];
        float r, g, b;
        if (s == 0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
            float p = 2f * l - q;
            r = hue2rgb(p, q, h + 1f/3f);
            g = hue2rgb(p, q, h);
            b = hue2rgb(p, q, h - 1f/3f);
        }
        return 0xFF000000 | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }
    private static float hue2rgb(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f/6f) return p + (q - p) * 6f * t;
        if (t < 1f/2f) return q;
        if (t < 2f/3f) return p + (q - p) * (2f/3f - t) * 6f;
        return p;
    }

    private static int blendWhiteToBlue(double norm) {
        int start = 0xFFFFFFFF;
        int end = 0xFF2B7FFF;
        return lerpColor(start, end, norm);
    }

    private static int[] uvColor(double uv) {
        double t = Math.max(0.0, Math.min(1.0, uv / 11.0));
        int start = lerpColor(0xFF66BB6A, 0xFFFFEB3B, t); // green->yellow
        int end = lerpColor(0xFFFFEB3B, 0xFFF44336, t);   // yellow->red
        return new int[]{ start, end };
    }

    private static int lerpColor(int a, int b, double t) {
        int ia = (a >> 24) & 0xff;
        int ir = (a >> 16) & 0xff;
        int ig = (a >> 8) & 0xff;
        int ib = a & 0xff;
        int ja = (b >> 24) & 0xff;
        int jr = (b >> 16) & 0xff;
        int jg = (b >> 8) & 0xff;
        int jb = b & 0xff;
        int aa = (int)(ia + (ja - ia) * t);
        int rr = (int)(ir + (jr - ir) * t);
        int gg = (int)(ig + (jg - ig) * t);
        int bb = (int)(ib + (jb - ib) * t);
        return ((aa & 0xff) << 24) | ((rr & 0xff) << 16) | ((gg & 0xff) << 8) | (bb & 0xff);
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

    // Format a human readable age like "(5m)" or "(30s)"; returns empty string on error
    private static String formatAge(long ts) {
        try {
            if (ts <= 0) return "";
            long age = System.currentTimeMillis() - ts;
            if (age < 1000L) return "(now)";
            long secs = age / 1000L;
            if (secs < 60L) return "(" + secs + "s)";
            long mins = secs / 60L;
            if (mins < 60L) return "(" + mins + "m)";
            long hours = mins / 60L;
            return "(" + hours + "h)";
        } catch (Throwable ignored) {
            return "";
        }
    }

    // Return a compact "Last fetched" label using a time or relative age depending on how recent
    private static String lastFetchedLabel(long ts) {
        try {
            if (ts <= 0) return "Last fetched (unknown)";
            long age = System.currentTimeMillis() - ts;
            // If very recent, show relative age like (now)/(5s)/(3m)
            if (age < 60L * 1000L) {
                String a = formatAge(ts);
                return "Last fetched " + a;
            }
            // Otherwise show a simple time like HH:MM in the device default timezone
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm");
            String t = fmt.format(new java.util.Date(ts));
            return "Last fetched " + t;
        } catch (Throwable ignored) {
            return "Last fetched";
        }
    }
}
