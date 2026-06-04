package com.ultraaudio.recorder.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.content.res.ColorStateList;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.ultraaudio.recorder.R;
import com.ultraaudio.recorder.audio.AudioEngine;
import com.ultraaudio.recorder.audio.AudioRecorder;
import com.ultraaudio.recorder.audio.MicrophoneArrayManager;
import com.ultraaudio.recorder.dsp.DSPProcessor;
import com.ultraaudio.recorder.dsp.FFTAnalyzer;
import com.ultraaudio.recorder.service.AudioRecordingService;
import com.ultraaudio.recorder.visualization.LevelMeterView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private static final String PREFS_NAME = "UltraAudioPrefs";
    private static final String PREF_HIGH_RES = "pref_high_res";
    private static final String PREF_LOW_LATENCY = "pref_low_latency";
    private static final String PREF_BACKGROUND = "pref_background_recording";

    // Core components
    private AudioEngine audioEngine;
    private AudioRecorder audioRecorder;
    private DSPProcessor dspProcessor;
    private FFTAnalyzer fftAnalyzer;
    private MicrophoneArrayManager arrayManager;
    
    // UI components
    private TabLayout tabLayout;
    private LevelMeterView levelMeter;
    private Spinner micSpinner, outputSpinner;
    private ImageButton btnRecord, btnStop, btnListen;
    private TextView tvStatus, tvFormat, tvDuration;
    
    // State
    private boolean isRecording = false;
    private boolean isListening = false;
    private List<AudioDeviceInfo> inputDevices = new ArrayList<>();
    private List<AudioDeviceInfo> outputDevices = new ArrayList<>();
    private AudioRecorder.Format selectedFormat = AudioRecorder.Format.WAV_PCM_16;
    private int selectedSampleRate = 48000;
    private int selectedChannels = 1;
    
    // Service
    private AudioRecordingService recordingService;
    private boolean serviceBound = false;
    
    // Duration timer
    private Thread durationThread;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        requestPermissions();
        initComponents();
        initUI();
        setupTabs();
        refreshDevices();
    }
    
    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.RECORD_AUDIO);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        
        List<String> needed = new ArrayList<>();
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed.add(perm);
            }
        }
        
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }
    
    private void initComponents() {
        audioEngine = new AudioEngine(this);
        audioRecorder = new AudioRecorder();
        arrayManager = new MicrophoneArrayManager(this);

        // Apply saved audio settings
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        audioEngine.setLowLatencyMode(prefs.getBoolean(PREF_LOW_LATENCY, true));
        boolean highResEnabled = prefs.getBoolean(PREF_HIGH_RES, false);
        if (highResEnabled) {
            audioEngine.setHighResolutionMode(true);
            selectedSampleRate = audioEngine.getSampleRate();
        }

        dspProcessor = new DSPProcessor(audioEngine.getSampleRate());
        fftAnalyzer = new FFTAnalyzer(2048, audioEngine.getSampleRate());
        
        // Wire up DSP processor
        dspProcessor.setOutputListener((data, sr, ch) -> {
            // Feed to recorder if recording
            if (isRecording) {
                audioRecorder.onAudioData(data, sr, ch);
            }
            
            // Feed to FFT analyzer
            fftAnalyzer.analyze(data);
            
            // Update level meter
            runOnUiThread(() -> {
                if (levelMeter != null) {
                    levelMeter.setDBA(fftAnalyzer.getDBA());
                    levelMeter.setSones(fftAnalyzer.getSones());
                }
            });
        });
        
        // Connect audio engine -> DSP processor
        audioEngine.addDataListener(dspProcessor);
        
        // Level listener
        audioEngine.addLevelListener((rms, peak, dbSPL) -> {
            runOnUiThread(() -> {
                if (levelMeter != null) {
                    levelMeter.updateLevel(rms[0], peak[0], dbSPL[0]);
                }
            });
        });
    }
    
    private void initUI() {
        tabLayout = findViewById(R.id.tab_layout);
        levelMeter = findViewById(R.id.level_meter);
        btnRecord = findViewById(R.id.btn_record);
        btnStop = findViewById(R.id.btn_stop);
        btnListen = findViewById(R.id.btn_listen);
        tvStatus = findViewById(R.id.tv_status);
        tvDuration = findViewById(R.id.tv_duration);
        tvFormat = findViewById(R.id.tv_status); // Re-use status bar or add hidden view if missing
        
        micSpinner = findViewById(R.id.spinner_mic);
        outputSpinner = findViewById(R.id.spinner_output);
        
        btnRecord.setOnClickListener(v -> toggleRecording());
        btnStop.setOnClickListener(v -> stopAll());
        
        TextView tvListenStatus = findViewById(R.id.tv_listen_status);
        btnListen.setOnClickListener(v -> {
            toggleListening();
            if (audioEngine.isMonitoring()) {
                tvListenStatus.setText("LISTEN ON");
                tvListenStatus.setTextColor(ContextCompat.getColor(this, R.color.accent));
                btnListen.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent)));
            } else {
                tvListenStatus.setText("LISTEN OFF");
                tvListenStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                btnListen.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_dark)));
            }
        });
        
        findViewById(R.id.btn_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        findViewById(R.id.btn_dashboard).setOnClickListener(v -> {
            showFragment(new DashboardFragment());
        });

        SeekBar gainSlider = findViewById(R.id.main_gain_slider);
        TextView tvGain = findViewById(R.id.tv_main_gain_val);
        gainSlider.setMax(3900); // 0-3900 maps to x1.0 to x40.0
        gainSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float gain = 1.0f + (progress / 100.0f);
                tvGain.setText(String.format("x%.1f (%.0f%%)", gain, gain * 100));
                audioEngine.setMasterGain(gain);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Mic selection
        if (micSpinner != null) {
            micSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position < inputDevices.size()) {
                        audioEngine.setInputDevice(inputDevices.get(position));
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
        
        // Output selection
        if (outputSpinner != null) {
            outputSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position < outputDevices.size()) {
                        audioEngine.setOutputDevice(outputDevices.get(position));
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
        
        updateFormatDisplay();
    }
    
    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Recorder"));
        tabLayout.addTab(tabLayout.newTab().setText("Analysis"));
        tabLayout.addTab(tabLayout.newTab().setText("DSP"));
        tabLayout.addTab(tabLayout.newTab().setText("Array"));
        tabLayout.addTab(tabLayout.newTab().setText("Files"));
        
        // Show first fragment
        showFragment(new RecorderFragment());
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                Fragment fragment;
                switch (tab.getPosition()) {
                    case 0: fragment = new RecorderFragment(); break;
                    case 1: fragment = new AnalysisFragment(); break;
                    case 2: fragment = new DSPFragment(); break;
                    case 3: fragment = new ArrayFragment(); break;
                    case 4: fragment = new FilesFragment(); break;
                    default: fragment = new RecorderFragment(); break;
                }
                showFragment(fragment);
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }
    
    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.content_frame, fragment)
            .commit();
    }
    
    private void refreshDevices() {
        inputDevices = audioEngine.getInputDevices();
        outputDevices = audioEngine.getOutputDevices();
        
        List<String> inputNames = new ArrayList<>();
        for (AudioDeviceInfo device : inputDevices) {
            inputNames.add(AudioEngine.getDeviceTypeName(device.getType()) + " #" + device.getId());
        }
        
        List<String> outputNames = new ArrayList<>();
        for (AudioDeviceInfo device : outputDevices) {
            outputNames.add(AudioEngine.getDeviceTypeName(device.getType()) + " #" + device.getId());
        }
        
        if (micSpinner != null) {
            ArrayAdapter<String> inputAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, inputNames);
            inputAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            micSpinner.setAdapter(inputAdapter);
        }
        
        if (outputSpinner != null) {
            ArrayAdapter<String> outputAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, outputNames);
            outputAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            outputSpinner.setAdapter(outputAdapter);
        }
    }
    
    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }
    
    private void startRecording() {
        if (!audioEngine.isCapturing()) {
            if (!audioEngine.startCapture()) {
                Toast.makeText(this, "Failed to start audio capture", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        // Setup recorder
        audioRecorder.setFormat(selectedFormat);
        audioRecorder.setSampleRate(selectedSampleRate);
        audioRecorder.setChannels(selectedChannels);
        
        String filename = generateFilename();
        String path = getRecordingDirectory() + "/" + filename;
        
        if (audioRecorder.startRecording(path)) {
            isRecording = true;
            tvStatus.setText("Recording: " + filename);
            btnRecord.setAlpha(0.5f);
            
            // Start foreground service
            startRecordingService();
            startDurationTimer();
        } else {
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopRecording() {
        isRecording = false;
        audioRecorder.stopRecording();
        tvStatus.setText("Recording saved");
        btnRecord.setAlpha(1.0f);
        stopDurationTimer();
        
        if (!isListening) {
            stopRecordingService();
        }
    }
    
    private void toggleListening() {
        if (isListening) {
            stopListening();
        } else {
            startListening();
        }
    }
    
    private void startListening() {
        if (!audioEngine.isCapturing()) {
            if (!audioEngine.startCapture()) {
                Toast.makeText(this, "Failed to start audio capture", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        audioEngine.startMonitoring();
        isListening = true;
        tvStatus.setText("Listening...");
        btnListen.setAlpha(0.5f);
        
        if (!isRecording) {
            startRecordingService();
        }
    }
    
    private void stopListening() {
        audioEngine.stopMonitoring();
        isListening = false;
        btnListen.setAlpha(1.0f);
        
        if (!isRecording) {
            tvStatus.setText("Ready");
            stopRecordingService();
        }
    }
    
    private void stopAll() {
        if (isRecording) stopRecording();
        if (isListening) stopListening();
        audioEngine.stopCapture();
        tvStatus.setText("Ready");
        tvDuration.setText("00:00:00");
    }
    
    private String generateFilename() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String timestamp = sdf.format(new Date());
        String ext = getFormatExtension();
        return "REC_" + timestamp + ext;
    }
    
    private String getFormatExtension() {
        switch (selectedFormat) {
            case WAV_PCM_16:
            case WAV_PCM_24:
            case WAV_PCM_32:
            case WAV_FLOAT_32:
            case WAV_FLOAT_64:
            case WAV_ALAW:
            case WAV_ULAW:
            case WAV_ADPCM:
                return ".wav";
            case FLAC:
                return ".flac";
            case M4A_AAC:
                return ".m4a";
            case OGG_VORBIS:
                return ".ogg";
            case SPEEX:
                return ".spx";
            default:
                return ".wav";
        }
    }
    
    private String getRecordingDirectory() {
        File dir = new File(Environment.getExternalStorageDirectory(), "UltraAudioRecorder");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // Fallback to app-specific directory
        if (!dir.exists() || !dir.canWrite()) {
            dir = new File(getExternalFilesDir(null), "Recordings");
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }
    
    private void updateFormatDisplay() {
        if (tvFormat == null) return;
        String formatStr = selectedSampleRate / 1000 + "kHz / " + 
            getBitDepthStr() + " / " + 
            (selectedChannels == 1 ? "Mono" : "Stereo");
        tvFormat.setText(formatStr);
    }
    
    private String getBitDepthStr() {
        switch (selectedFormat) {
            case WAV_PCM_16: return "16bit";
            case WAV_PCM_24: return "24bit";
            case WAV_PCM_32: return "32bit";
            case WAV_FLOAT_32: return "32bit float";
            case WAV_FLOAT_64: return "64bit float";
            case WAV_ALAW: return "A-Law";
            case WAV_ULAW: return "U-Law";
            case M4A_AAC: return "AAC";
            case FLAC: return "FLAC";
            case OGG_VORBIS: return "Vorbis";
            default: return "16bit";
        }
    }
    
    private void startDurationTimer() {
        durationThread = new Thread(() -> {
            while (isRecording) {
                double duration = audioRecorder.getRecordingDuration();
                int hours = (int) (duration / 3600);
                int minutes = (int) ((duration % 3600) / 60);
                int seconds = (int) (duration % 60);
                String timeStr = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
                runOnUiThread(() -> tvDuration.setText(timeStr));
                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
            }
        });
        durationThread.start();
    }
    
    private void stopDurationTimer() {
        if (durationThread != null) {
            durationThread.interrupt();
            durationThread = null;
        }
    }
    
    private void startRecordingService() {
        Intent intent = new Intent(this, AudioRecordingService.class);
        intent.setAction(AudioRecordingService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
    
    private void stopRecordingService() {
        Intent intent = new Intent(this, AudioRecordingService.class);
        intent.setAction(AudioRecordingService.ACTION_STOP);
        startService(intent);
    }
    
    // Public accessors for fragments
    public AudioEngine getAudioEngine() { return audioEngine; }
    public DSPProcessor getDSPProcessor() { return dspProcessor; }
    public FFTAnalyzer getFFTAnalyzer() { return fftAnalyzer; }
    public MicrophoneArrayManager getArrayManager() { return arrayManager; }
    public AudioRecorder getAudioRecorder() { return audioRecorder; }
    
    public void setSelectedFormat(AudioRecorder.Format format) {
        this.selectedFormat = format;
        updateFormatDisplay();
    }
    
    public void setSelectedSampleRate(int rate) {
        this.selectedSampleRate = rate;
        audioEngine.setSampleRate(rate);
        updateFormatDisplay();
    }
    
    public void setSelectedChannels(int channels) {
        this.selectedChannels = channels;
        audioEngine.setChannelConfig(channels);
        updateFormatDisplay();
    }
    
    public String getRecordingDir() {
        return getRecordingDirectory();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAll();
        audioEngine.release();
    }
}
