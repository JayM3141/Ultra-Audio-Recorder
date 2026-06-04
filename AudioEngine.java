package com.ultraaudio.recorder.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Advanced Audio Engine supporting up to 7.1 channels, x40 per-channel gain,
 * and real-time multi-microphone array management.
 */
public class AudioEngine {
    private static final String TAG = "AudioEngine";
    
    private int sampleRate = 48000;
    private int channelMask = AudioFormat.CHANNEL_IN_MONO;
    private int channelCount = 1;
    private int audioFormat = AudioFormat.ENCODING_PCM_FLOAT;
    private int bufferSize;
    private boolean lowLatencyMode = true;
    private boolean highResolutionMode = false;
    
    // Per-channel gain array (supports up to 8 channels)
    private float[] channelGains = new float[8];
    private float masterGain = 1.0f;
    
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private AudioManager audioManager;
    private Context context;
    
    private AtomicBoolean isCapturing = new AtomicBoolean(false);
    private AtomicBoolean isMonitoring = new AtomicBoolean(false);
    
    private Thread processingThread;
    
    private CopyOnWriteArrayList<AudioDataListener> dataListeners = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<AudioLevelListener> levelListeners = new CopyOnWriteArrayList<>();
    
    private AudioDeviceInfo selectedInputDevice;
    private AudioDeviceInfo selectedOutputDevice;
    
    private float[] captureBuffer;
    private float[] processedBuffer;
    
    public interface AudioDataListener {
        void onAudioData(float[] data, int sampleRate, int channels);
    }
    
    public interface AudioLevelListener {
        void onLevel(float[] rms, float[] peak, float[] dbSPL);
    }
    
    public AudioEngine(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        for (int i = 0; i < 8; i++) channelGains[i] = 1.0f;
        initOptimalSettings();
    }
    
    private void initOptimalSettings() {
        String optimalRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        if (optimalRate != null) {
            sampleRate = Integer.parseInt(optimalRate);
        }
        recalculateBufferSize();
    }
    
    public void setSampleRate(int rate) {
        this.sampleRate = rate;
        recalculateBufferSize();
    }
    
    public void setLowLatencyMode(boolean enabled) {
        this.lowLatencyMode = enabled;
    }
    
    public void setHighResolutionMode(boolean enabled) {
        this.highResolutionMode = enabled;
        if (enabled && sampleRate < 96000) {
            setSampleRate(96000);
        }
    }
    
    public void setChannelConfig(int channels) {
        this.channelCount = channels;
        switch (channels) {
            case 1: channelMask = AudioFormat.CHANNEL_IN_MONO; break;
            case 2: channelMask = AudioFormat.CHANNEL_IN_STEREO; break;
            default: 
                // For 4, 6, 8 channels, use the integer value directly as masks are API-dependent
                if (channels == 4) channelMask = 0xcc; // QUAD
                else if (channels == 6) channelMask = 0xfc; // 5.1
                else if (channels == 8) channelMask = 0x3fc; // 7.1
                else {
                    channelMask = AudioFormat.CHANNEL_IN_MONO;
                    this.channelCount = 1;
                }
                break;
        }
        recalculateBufferSize();
    }
    
    public void setMasterGain(float gain) {
        this.masterGain = gain;
    }
    
    public void setChannelGain(int channel, float gain) {
        if (channel >= 0 && channel < 8) {
            channelGains[channel] = gain;
        }
    }
    
    public float getChannelGain(int channel) {
        return (channel >= 0 && channel < 8) ? channelGains[channel] : 1.0f;
    }
    
    private void recalculateBufferSize() {
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelMask, audioFormat);
        // Ensure buffer size is a multiple of 512 samples per channel for low latency
        int minSamples = 512 * channelCount;
        bufferSize = Math.max(bufferSize, minSamples * 4); // 4 bytes per float
    }
    
    public void setInputDevice(AudioDeviceInfo device) {
        this.selectedInputDevice = device;
        if (audioRecord != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioRecord.setPreferredDevice(device);
        }
    }
    
    public void setOutputDevice(AudioDeviceInfo device) {
        this.selectedOutputDevice = device;
        if (audioTrack != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioTrack.setPreferredDevice(device);
        }
    }
    
    public List<AudioDeviceInfo> getInputDevices() {
        List<AudioDeviceInfo> inputs = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                inputs.add(device);
            }
        }
        return inputs;
    }
    
    public List<AudioDeviceInfo> getOutputDevices() {
        List<AudioDeviceInfo> outputs = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                outputs.add(device);
            }
        }
        return outputs;
    }
    
    public boolean startCapture() {
        if (isCapturing.get()) return true;
        
        try {
            int source = MediaRecorder.AudioSource.UNPROCESSED;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) source = MediaRecorder.AudioSource.MIC;
            
            audioRecord = new AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .build();
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) return false;
            
            if (selectedInputDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioRecord.setPreferredDevice(selectedInputDevice);
            }
            
            int frameSize = 512 * channelCount;
            captureBuffer = new float[frameSize];
            processedBuffer = new float[frameSize];
            
            audioRecord.startRecording();
            isCapturing.set(true);
            
            startCaptureLoop();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting capture", e);
            return false;
        }
    }
    
    private void startCaptureLoop() {
        processingThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            
            float[] rms = new float[channelCount];
            float[] peak = new float[channelCount];
            float[] dbSPL = new float[channelCount];
            
            while (isCapturing.get()) {
                int read = audioRecord.read(captureBuffer, 0, captureBuffer.length, AudioRecord.READ_BLOCKING);
                if (read > 0) {
                    // Reset levels for this frame
                    for (int c = 0; c < channelCount; c++) {
                        rms[c] = 0; peak[c] = 0;
                    }

                    // Process interleaved channels
                    for (int i = 0; i < read; i++) {
                        int channelIndex = i % channelCount;
                        float gain = masterGain * channelGains[channelIndex];
                        
                        float sample = captureBuffer[i] * gain;
                        // Hard clip at 1.0
                        processedBuffer[i] = Math.max(-1.0f, Math.min(1.0f, sample));
                        
                        // Level calculations
                        float abs = Math.abs(processedBuffer[i]);
                        rms[channelIndex] += processedBuffer[i] * processedBuffer[i];
                        if (abs > peak[channelIndex]) peak[channelIndex] = abs;
                    }
                    
                    // Finalize level calculations
                    int samplesPerChannel = read / channelCount;
                    for (int c = 0; c < channelCount; c++) {
                        rms[c] = (float) Math.sqrt(rms[c] / samplesPerChannel);
                        dbSPL[c] = 20.0f * (float) Math.log10(rms[c] + 1e-10f) + 94.0f;
                    }
                    
                    // Dispatch to level listeners
                    for (AudioLevelListener listener : levelListeners) {
                        listener.onLevel(rms, peak, dbSPL);
                    }
                    
                    // Dispatch to data listeners
                    for (AudioDataListener listener : dataListeners) {
                        listener.onAudioData(processedBuffer, sampleRate, channelCount);
                    }
                    
                    // Monitor output
                    if (isMonitoring.get() && audioTrack != null) {
                        audioTrack.write(processedBuffer, 0, read, AudioTrack.WRITE_NON_BLOCKING);
                    }
                }
            }
        }, "AudioCapture");
        processingThread.setPriority(Thread.MAX_PRIORITY);
        processingThread.start();
    }
    
    public void startMonitoring() {
        if (isMonitoring.get()) return;
        try {
            int outMask;
            switch (channelCount) {
                case 1: outMask = AudioFormat.CHANNEL_OUT_MONO; break;
                case 2: outMask = AudioFormat.CHANNEL_OUT_STEREO; break;
                case 4: outMask = AudioFormat.CHANNEL_OUT_QUAD; break;
                case 6: outMask = AudioFormat.CHANNEL_OUT_5POINT1; break;
                case 8: outMask = AudioFormat.CHANNEL_OUT_7POINT1_SURROUND; break;
                default: outMask = AudioFormat.CHANNEL_OUT_MONO; break;
            }
            
            int outBufSize = AudioTrack.getMinBufferSize(sampleRate, outMask, audioFormat);
            
            AudioTrack.Builder builder = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(outMask)
                    .build())
                .setBufferSizeInBytes(outBufSize);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.setTransferMode(AudioTrack.MODE_STREAM);
                if (lowLatencyMode) {
                    builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
                }
            }

            audioTrack = builder.build();
            
            if (selectedOutputDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioTrack.setPreferredDevice(selectedOutputDevice);
            }
            audioTrack.play();
            isMonitoring.set(true);
        } catch (Exception e) { Log.e(TAG, "Error starting monitor", e); }
    }
    
    public void stopMonitoring() {
        isMonitoring.set(false);
        if (audioTrack != null) {
            try { audioTrack.stop(); audioTrack.release(); } catch (Exception e) {}
            audioTrack = null;
        }
    }
    
    public void stopCapture() {
        isCapturing.set(false);
        stopMonitoring();
        if (processingThread != null) {
            try { processingThread.join(500); } catch (Exception e) {}
        }
        if (audioRecord != null) {
            try { audioRecord.stop(); audioRecord.release(); } catch (Exception e) {}
            audioRecord = null;
        }
    }
    
    public void addDataListener(AudioDataListener listener) { dataListeners.add(listener); }
    public void removeDataListener(AudioDataListener listener) { dataListeners.remove(listener); }
    public void addLevelListener(AudioLevelListener listener) { levelListeners.add(listener); }
    public void removeLevelListener(AudioLevelListener listener) { levelListeners.remove(listener); }
    public boolean isCapturing() { return isCapturing.get(); }
    public boolean isMonitoring() { return isMonitoring.get(); }
    public int getSampleRate() { return sampleRate; }
    public int getChannelCount() { return channelCount; }
    public void release() { stopCapture(); dataListeners.clear(); levelListeners.clear(); }

    public static String getDeviceTypeName(int type) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            switch (type) {
                case AudioDeviceInfo.TYPE_BUILTIN_MIC: return "Internal Mic";
                case AudioDeviceInfo.TYPE_USB_DEVICE: return "Pro USB Mic";
                case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB Audio Interface";
                case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Wired Analog Mic";
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth Array";
                default: return "Audio Device";
            }
        }
        return "Microphone";
    }
}
