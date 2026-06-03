package com.ultraaudio.recorder.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.ultraaudio.recorder.R;

public class SettingsActivity extends AppCompatActivity {
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

        // Implementation of settings toggles
        setupToggles();
    }

    private void setupToggles() {
        Switch swHighRes = findViewById(R.id.sw_high_res);
        Switch swLowLatency = findViewById(R.id.sw_low_latency);
        Switch swBackground = findViewById(R.id.sw_background);

        swHighRes.setChecked(true);
        swLowLatency.setChecked(true);
        swBackground.setChecked(true);
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
