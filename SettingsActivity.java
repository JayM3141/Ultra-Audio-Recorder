package com.ultraaudio.recorder.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.ultraaudio.recorder.R;

public class SettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "UltraAudioPrefs";
    private static final String PREF_HIGH_RES = "pref_high_res";
    private static final String PREF_LOW_LATENCY = "pref_low_latency";
    private static final String PREF_BACKGROUND = "pref_background_recording";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }

        setupToggles();
    }

    private void setupToggles() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        Switch swHighRes = findViewById(R.id.sw_high_res);
        Switch swLowLatency = findViewById(R.id.sw_low_latency);
        Switch swBackground = findViewById(R.id.sw_background);

        swHighRes.setChecked(prefs.getBoolean(PREF_HIGH_RES, false));
        swLowLatency.setChecked(prefs.getBoolean(PREF_LOW_LATENCY, true));
        swBackground.setChecked(prefs.getBoolean(PREF_BACKGROUND, true));

        swHighRes.setOnCheckedChangeListener((buttonView, isChecked) ->
            prefs.edit().putBoolean(PREF_HIGH_RES, isChecked).apply());

        swLowLatency.setOnCheckedChangeListener((buttonView, isChecked) ->
            prefs.edit().putBoolean(PREF_LOW_LATENCY, isChecked).apply());

        swBackground.setOnCheckedChangeListener((buttonView, isChecked) ->
            prefs.edit().putBoolean(PREF_BACKGROUND, isChecked).apply());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
