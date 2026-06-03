package com.ultraaudio.recorder.audio;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a single microphone input source that can be part of an array.
 */
public class MicrophoneInput {
    private static final String TAG = "MicrophoneInput";
    
    private AudioDeviceInfo deviceInfo;
    private AudioRecord audioRecord;
    private int sampleRate;
    private int channelConfig;
    private int audioFormat;
    private int bufferSize;
    private AtomicBoolean isActive = new AtomicBoolean(false);
    private String label;
    private float positionX, positionY, positionZ; // Position in array space
    private float calibrationOffset = 0.0f; // dB offset for calibration
    private float[] lastBuffer;
    private MicDataCallback callback;
    
    public interface MicDataCallback {
        void onMicData(MicrophoneInput mic, float[] data, int samplesRead);
    }
    
    public MicrophoneInput(AudioDeviceInfo deviceInfo, int sampleRate, int channels, int bitDepth) {
        this.deviceInfo = deviceInfo;
        this.sampleRate = sampleRate;
        this.channelConfig = channels == 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        this.audioFormat = bitDepth == 16 ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_FLOAT;
        this.label = AudioEngine.getDeviceTypeName(deviceInfo.getType()) + " #" + deviceInfo.getId();
        
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        if (bufferSize < 0) bufferSize = sampleRate * 4;
    }
    
    public void setPosition(float x, float y, float z) {
        this.positionX = x;
        this.positionY = y;
        this.positionZ = z;
    }
    
    public void setCalibrationOffset(float dbOffset) {
        this.calibrationOffset = dbOffset;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public void setCallback(MicDataCallback callback) {
        this.callback = callback;
    }
    
    public boolean start() {
        try {
            audioRecord = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build())
                .setBufferSizeInBytes(bufferSize * 2)
                .build();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioRecord.setPreferredDevice(deviceInfo);
            }
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to init AudioRecord for " + label);
                return false;
            }
            
            lastBuffer = new float[bufferSize / 4];
            audioRecord.startRecording();
            isActive.set(true);
            
            new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                while (isActive.get()) {
                    int read = audioRecord.read(lastBuffer, 0, lastBuffer.length, AudioRecord.READ_BLOCKING);
                    if (read > 0 && callback != null) {
                        // Apply calibration
                        if (calibrationOffset != 0.0f) {
                            float gain = (float) Math.pow(10.0, calibrationOffset / 20.0);
                            for (int i = 0; i < read; i++) {
                                lastBuffer[i] *= gain;
                            }
                        }
                        callback.onMicData(this, lastBuffer, read);
                    }
                }
            }, "MicInput-" + label).start();
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting mic " + label, e);
            return false;
        }
    }
    
    public void stop() {
        isActive.set(false);
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping mic", e);
            }
            audioRecord = null;
        }
    }
    
    public boolean isActive() { return isActive.get(); }
    public String getLabel() { return label; }
    public float getPositionX() { return positionX; }
    public float getPositionY() { return positionY; }
    public float getPositionZ() { return positionZ; }
    public float getCalibrationOffset() { return calibrationOffset; }
    public AudioDeviceInfo getDeviceInfo() { return deviceInfo; }
    public int getSampleRate() { return sampleRate; }
}
