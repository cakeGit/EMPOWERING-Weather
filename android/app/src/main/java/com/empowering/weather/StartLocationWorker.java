package com.empowering.weather;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Worker used to start LocationUpdatesService in a WorkManager-safe way (used at boot).
 */
public class StartLocationWorker extends Worker {
    private static final String TAG = "StartLocationWorker";

    public StartLocationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE);
        try {
            Intent svc = new Intent(ctx, LocationUpdatesService.class);
            try {
                // Prefer a normal startService call so the system doesn't expect a
                // subsequent startForeground() call. If this fails (background-start
                // restrictions), we'll record the error and retry later.
                ctx.startService(svc);
            } catch (Throwable startErr) {
                String msg = startErr.getClass().getSimpleName() + ": " + startErr.getMessage();
                prefs.edit().putString("location_service_start_status", "error: " + msg).putLong("location_service_start_time", System.currentTimeMillis()).apply();
                Log.w(TAG, "failed to start service", startErr);
                return Result.retry();
            }
            prefs.edit().putString("location_service_start_status", "ok").putLong("location_service_start_time", System.currentTimeMillis()).apply();
            Log.i(TAG, "started LocationUpdatesService via WorkManager");
            return Result.success();
        } catch (Throwable t) {
            String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
            prefs.edit().putString("location_service_start_status", "error: " + msg).putLong("location_service_start_time", System.currentTimeMillis()).apply();
            Log.w(TAG, "failed to start service", t);
            return Result.retry();
        }
    }
}
