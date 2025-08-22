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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(svc);
            } else {
                ctx.startService(svc);
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
