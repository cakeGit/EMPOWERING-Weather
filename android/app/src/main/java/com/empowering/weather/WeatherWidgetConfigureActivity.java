package com.empowering.weather;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;

public class WeatherWidgetConfigureActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.weather_widget_configure);
        Bundle extras = getIntent().getExtras();
        int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        EditText inputLat = findViewById(R.id.inputLat);
        EditText inputLon = findViewById(R.id.inputLon);
        Button btnSave = findViewById(R.id.btnSave);

        SharedPreferences prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE);
        double existingLat = Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(0)));
        double existingLon = Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(0)));
        if (!(existingLat == 0 && existingLon == 0)) {
            inputLat.setText(String.valueOf(existingLat));
            inputLon.setText(String.valueOf(existingLon));
        }

        int appWidgetIdFinal = appWidgetId;
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    double lat = Double.parseDouble(inputLat.getText().toString().trim());
                    double lon = Double.parseDouble(inputLon.getText().toString().trim());
                    prefs.edit()
                        .putLong("lat", Double.doubleToLongBits(lat))
                        .putLong("lon", Double.doubleToLongBits(lon))
                        .apply();
                } catch (Exception ignored) { }

                // Trigger an immediate update
                AppWidgetManager mgr = AppWidgetManager.getInstance(WeatherWidgetConfigureActivity.this);
                WeatherWidgetProvider provider = new WeatherWidgetProvider();
                provider.onUpdate(WeatherWidgetConfigureActivity.this, mgr, new int[]{ appWidgetIdFinal });

                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIdFinal);
                setResult(RESULT_OK, resultValue);
                finish();
            }
        });
    }
}
