package com.ultraaudio.recorder.audio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages multiple microphone inputs as an array for spatial audio capture.
 * Supports combining USB, wired, built-in, and Bluetooth microphones.
 */
public class MicrophoneArrayManager {
    private static final String TAG = "MicArrayManager";
    
    private Context context;
    private AudioManager audioManager;
    private List<MicrophoneInput> microphones = new ArrayList<>();
    private Map<Integer, float[]> micBuffers = new HashMap<>();
    private CopyOnWriteArrayList<ArrayDataListener> listeners = new CopyOnWriteArrayList<>();
    
    private int sampleRate = 48000;
    private int channels = 1;
    private int bitDepth = 32;
    private boolean isRunning = false;
    
    // Spatial analysis data
    private float[][] correlationMatrix;
    private float[] directionEstimates;
    
    public interface ArrayDataListener {
        void onArrayData(Map<Integer, float[]> allMicData, int sampleRate);
        void onSpatialUpdate(float[] directions, float[] levels);
    }
    
    public MicrophoneArrayManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }
    
    public void setSampleRate(int rate) { this.sampleRate = rate; }
    public void setChannels(int ch) { this.channels = ch; }
    public void setBitDepth(int bits) { this.bitDepth = bits; }
    
    public List<AudioDeviceInfo> getAvailableInputDevices() {
        List<AudioDeviceInfo> devices = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] allDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (AudioDeviceInfo device : allDevices) {
                devices.add(device);
            }
        }
        return devices;
    }
    
    public MicrophoneInput addMicrophone(AudioDeviceInfo device) {
        MicrophoneInput mic = new MicrophoneInput(device, sampleRate, channels, bitDepth);
        mic.setCallback((m, data, samplesRead) -> {
            synchronized (micBuffers) {
                float[] copy = new float[samplesRead];
                System.arraycopy(data, 0, copy, 0, samplesRead);
                micBuffers.put(m.getDeviceInfo().getId(), copy);
            }
            
            // Notify listeners when we have data from all mics
            if (micBuffers.size() >= microphones.size()) {
                Map<Integer, float[]> snapshot;
                synchronized (micBuffers) {
                    snapshot = new HashMap<>(micBuffers);
                }
                for (ArrayDataListener listener : listeners) {
                    listener.onArrayData(snapshot, sampleRate);
                }
                
                // Compute spatial analysis
                computeSpatialAnalysis(snapshot);
            }
        });
        microphones.add(mic);
        return mic;
    }
    
    public void removeMicrophone(MicrophoneInput mic) {
        mic.stop();
        microphones.remove(mic);
        micBuffers.remove(mic.getDeviceInfo().getId());
    }
    
    public boolean startAll() {
        isRunning = true;
        boolean allStarted = true;
        for (MicrophoneInput mic : microphones) {
            if (!mic.start()) {
                Log.e(TAG, "Failed to start: " + mic.getLabel());
                allStarted = false;
            }
        }
        return allStarted;
    }
    
    public void stopAll() {
        isRunning = false;
        for (MicrophoneInput mic : microphones) {
            mic.stop();
        }
        micBuffers.clear();
    }
    
    private void computeSpatialAnalysis(Map<Integer, float[]> data) {
        int numMics = data.size();
        if (numMics < 2) return;
        
        float[] levels = new float[numMics];
        float[] directions = new float[numMics];
        
        int idx = 0;
        for (Map.Entry<Integer, float[]> entry : data.entrySet()) {
            float[] buffer = entry.getValue();
            float rms = 0;
            for (float sample : buffer) {
                rms += sample * sample;
            }
            rms = (float) Math.sqrt(rms / buffer.length);
            levels[idx] = 20.0f * (float) Math.log10(rms + 1e-10f);
            
            // Simple TDOA-based direction estimation
            directions[idx] = estimateDirection(idx, data);
            idx++;
        }
        
        updateLatestData(directions, levels);
        for (ArrayDataListener listener : listeners) {
            listener.onSpatialUpdate(directions, levels);
        }
    }
    
    private float estimateDirection(int micIdx, Map<Integer, float[]> data) {
        // Cross-correlation based time-delay estimation
        // Simplified for real-time performance
        List<float[]> buffers = new ArrayList<>(data.values());
        if (micIdx >= buffers.size() - 1) return 0;
        
        float[] ref = buffers.get(0);
        float[] target = buffers.get(micIdx);
        
        int maxLag = Math.min(ref.length, target.length) / 4;
        float maxCorr = 0;
        int bestLag = 0;
        
        for (int lag = -maxLag; lag < maxLag; lag++) {
            float corr = 0;
            int count = 0;
            for (int i = Math.max(0, lag); i < Math.min(ref.length, target.length + lag); i++) {
                int j = i - lag;
                if (j >= 0 && j < target.length) {
                    corr += ref[i] * target[j];
                    count++;
                }
            }
            if (count > 0) corr /= count;
            if (corr > maxCorr) {
                maxCorr = corr;
                bestLag = lag;
            }
        }
        
        // Convert lag to angle estimate (simplified)
        float timeDelay = (float) bestLag / sampleRate;
        float speedOfSound = 343.0f; // m/s
        return (float) Math.toDegrees(Math.asin(Math.min(1.0, Math.max(-1.0, 
            timeDelay * speedOfSound / 0.1f)))); // assuming 10cm spacing
    }
    
    public void addListener(ArrayDataListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(ArrayDataListener listener) {
        listeners.remove(listener);
    }
    
    public List<MicrophoneInput> getMicrophones() {
        return microphones;
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public int getMicCount() {
        return microphones.size();
    }

    public void startArrayCapture() {
        if (!isRunning) {
            // Auto-detect and add available mics if none added
            if (microphones.isEmpty()) {
                List<AudioDeviceInfo> devices = getAvailableInputDevices();
                for (AudioDeviceInfo device : devices) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (device.getType() == AudioDeviceInfo.TYPE_USB_DEVICE ||
                            device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
                            device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                            addMicrophone(device);
                        }
                    }
                }
            }
            startAll();
        }
    }

    private float[] latestLevels = new float[0];
    private float[] latestDirections = new float[0];

    public float[] getLatestLevels() {
        return latestLevels;
    }

    public float[] getLatestDirections() {
        return latestDirections;
    }

    public void calibrate() {
        // Implementation for calibration between devices
        // This would normally involve playing a chirp and measuring delays
    }

    private void updateLatestData(float[] directions, float[] levels) {
        this.latestDirections = directions;
        this.latestLevels = levels;
    }
}
