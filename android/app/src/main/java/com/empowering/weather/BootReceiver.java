package com.empowering.weather;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

/**
 * Receives BOOT_COMPLETED and schedules StartLocationWorker instead of starting a service directly.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String act = intent.getAction();
            Log.i(TAG, "received action: " + act);
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(StartLocationWorker.class).build();
            WorkManager.getInstance(context).enqueue(req);
        } catch (Throwable t) {
            Log.w(TAG, "failed to enqueue StartLocationWorker", t);
        }
    }
}
