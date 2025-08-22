package com.empowering.weather;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class NativeLocationActivity extends Activity {
    private static final int REQ_PERMS = 1423;
    private FusedLocationProviderClient fused;
    private LocationCallback lc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fused = LocationServices.getFusedLocationProviderClient(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, REQ_PERMS);
            return;
        }
        fetchLocationAndFinish();
    }

    private void fetchLocationAndFinish() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                Location last = null;
                try { last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Throwable ignored) {}
                try { Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); if (net != null && (last == null || net.getTime() > last.getTime())) last = net; } catch (Throwable ignored) {}
                if (last != null) {
                    saveAndFinish(last.getLatitude(), last.getLongitude());
                    return;
                }
            }

            // Request a single update from fused provider
            LocationRequest req = LocationRequest.create();
            req.setInterval(0);
            req.setFastestInterval(0);
            req.setNumUpdates(1);
            req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            lc = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    if (locationResult == null) {
                        saveAndFinish(0, 0);
                        return;
                    }
                    Location l = locationResult.getLastLocation();
                    if (l != null) saveAndFinish(l.getLatitude(), l.getLongitude()); else saveAndFinish(0,0);
                }
            };

            fused.requestLocationUpdates(req, lc, Looper.getMainLooper());
        } catch (Throwable t) {
            saveAndFinish(0,0);
        }
    }

    private void saveAndFinish(double lat, double lon) {
        try {
            SharedPreferences prefs = getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
        boolean hasLocation = !(lat == 0d && lon == 0d);
        prefs.edit()
            .putFloat("widget_lat", (float) lat)
            .putFloat("widget_lon", (float) lon)
            .putLong("widget_loc_time", System.currentTimeMillis())
            .putBoolean("widget_has_location", hasLocation)
            .apply();
        } catch (Throwable ignored) {}
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            boolean ok = false;
            if (grantResults != null && grantResults.length > 0) {
                for (int r : grantResults) if (r == PackageManager.PERMISSION_GRANTED) { ok = true; break; }
            }
            if (ok) {
                fetchLocationAndFinish();
            } else {
                saveAndFinish(0,0);
            }
        }
    }
}
