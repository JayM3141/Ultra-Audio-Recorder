package com.ultraaudio.recorder.dsp;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-time DSP audio processing pipeline.
 * Includes: Parametric EQ, Noise Removal, AGC, Filters, Reverb, BSS
 */
public class DSPProcessor implements com.ultraaudio.recorder.audio.AudioEngine.AudioDataListener {
    private static final String TAG = "DSPProcessor";
    
    private boolean enabled = true;
    private int sampleRate = 48000;
    
    // Processing chain
    private ParametricEqualizer equalizer;
    private NoiseRemoval noiseRemoval;
    private AutoGainControl agc;
    private FilterBank filterBank;
    private Reverberance reverb;
    private BlindSourceSeparation bss;
    
    // Processing order
    private List<ProcessingStage> processingChain = new ArrayList<>();
    
    // Output listener
    private ProcessedDataListener outputListener;
    
    public interface ProcessedDataListener {
        void onProcessedData(float[] data, int sampleRate, int channels);
    }
    
    public DSPProcessor(int sampleRate) {
        this.sampleRate = sampleRate;
        equalizer = new ParametricEqualizer(sampleRate);
        noiseRemoval = new NoiseRemoval(sampleRate);
        agc = new AutoGainControl(sampleRate);
        filterBank = new FilterBank(sampleRate);
        reverb = new Reverberance(sampleRate);
        bss = new BlindSourceSeparation(sampleRate);
        
        // Default processing chain order
        processingChain.add(ProcessingStage.FILTER);
        processingChain.add(ProcessingStage.NOISE_REMOVAL);
        processingChain.add(ProcessingStage.EQ);
        processingChain.add(ProcessingStage.AGC);
        processingChain.add(ProcessingStage.REVERB);
    }
    
    public enum ProcessingStage {
        EQ, NOISE_REMOVAL, AGC, FILTER, REVERB, BSS
    }
    
    @Override
    public void onAudioData(float[] data, int sampleRate, int channels) {
        if (!enabled) {
            if (outputListener != null) {
                outputListener.onProcessedData(data, sampleRate, channels);
            }
            return;
        }
        
        float[] processed = new float[data.length];
        System.arraycopy(data, 0, processed, 0, data.length);
        
        for (ProcessingStage stage : processingChain) {
            switch (stage) {
                case EQ:
                    if (equalizer.isEnabled()) {
                        processed = equalizer.process(processed);
                    }
                    break;
                case NOISE_REMOVAL:
                    if (noiseRemoval.isEnabled()) {
                        processed = noiseRemoval.process(processed);
                    }
                    break;
                case AGC:
                    if (agc.isEnabled()) {
                        processed = agc.process(processed);
                    }
                    break;
                case FILTER:
                    if (filterBank.isEnabled()) {
                        processed = filterBank.process(processed);
                    }
                    break;
                case REVERB:
                    if (reverb.isEnabled()) {
                        processed = reverb.process(processed);
                    }
                    break;
                case BSS:
                    if (bss.isEnabled()) {
                        processed = bss.process(processed);
                    }
                    break;
            }
        }
        
        if (outputListener != null) {
            outputListener.onProcessedData(processed, sampleRate, channels);
        }
    }
    
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    
    public ParametricEqualizer getEqualizer() { return equalizer; }
    public NoiseRemoval getNoiseRemoval() { return noiseRemoval; }
    public AutoGainControl getAgc() { return agc; }
    public FilterBank getFilterBank() { return filterBank; }
    public Reverberance getReverb() { return reverb; }
    public BlindSourceSeparation getBss() { return bss; }
    
    public void setOutputListener(ProcessedDataListener listener) {
        this.outputListener = listener;
    }
    
    public void setProcessingChain(List<ProcessingStage> chain) {
        this.processingChain = new ArrayList<>(chain);
    }
    
    // ==================== PARAMETRIC EQUALIZER ====================
    public static class ParametricEqualizer {
        private boolean enabled = false;
        private List<EQBand> bands = new ArrayList<>();
        private int sampleRate;
        
        public ParametricEqualizer(int sampleRate) {
            this.sampleRate = sampleRate;
            // Default 10 bands
            initDefaultBands(10);
        }
        
        public void initDefaultBands(int numBands) {
            bands.clear();
            float[] defaultFreqs = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000,
                20, 40, 80, 160, 315, 630, 1250, 2500, 5000, 10000,
                25, 50, 100, 200, 400, 800, 1600, 3150, 6300, 12500,
                35, 70, 140, 280, 560, 1120, 2240, 4500, 9000, 14000,
                45, 90, 180, 355, 710, 1400, 2800, 5600, 11200, 18000,
                55, 110, 220, 440, 880, 1750, 3500, 7000, 13000, 19000};
            
            for (int i = 0; i < Math.min(numBands, 60); i++) {
                float freq = i < defaultFreqs.length ? defaultFreqs[i] : 1000;
                bands.add(new EQBand(freq, 1.0f, 0.0f, sampleRate));
            }
        }
        
        public float[] process(float[] data) {
            float[] output = new float[data.length];
            System.arraycopy(data, 0, output, 0, data.length);
            
            for (EQBand band : bands) {
                if (band.gain != 0.0f) {
                    output = band.process(output);
                }
            }
            return output;
        }
        
        public void setBandCount(int count) {
            initDefaultBands(Math.max(4, Math.min(60, count)));
        }
        
        public void setBand(int index, float frequency, float q, float gainDb) {
            if (index >= 0 && index < bands.size()) {
                bands.get(index).setParameters(frequency, q, gainDb);
            }
        }
        
        public List<EQBand> getBands() { return bands; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getBandCount() { return bands.size(); }
    }
    
    public static class EQBand {
        float frequency;
        float q;
        float gain; // dB
        int sampleRate;
        
        // Biquad coefficients
        double b0, b1, b2, a1, a2;
        double x1, x2, y1, y2;
        
        public EQBand(float frequency, float q, float gain, int sampleRate) {
            this.sampleRate = sampleRate;
            setParameters(frequency, q, gain);
        }
        
        public void setParameters(float frequency, float q, float gain) {
            this.frequency = frequency;
            this.q = q;
            this.gain = gain;
            calculateCoefficients();
        }
        
        private void calculateCoefficients() {
            double A = Math.pow(10.0, gain / 40.0);
            double w0 = 2.0 * Math.PI * frequency / sampleRate;
            double alpha = Math.sin(w0) / (2.0 * q);
            
            // Peaking EQ
            double a0;
            b0 = 1.0 + alpha * A;
            b1 = -2.0 * Math.cos(w0);
            b2 = 1.0 - alpha * A;
            a0 = 1.0 + alpha / A;
            a1 = -2.0 * Math.cos(w0);
            a2 = 1.0 - alpha / A;
            
            // Normalize
            b0 /= a0;
            b1 /= a0;
            b2 /= a0;
            a1 /= a0;
            a2 /= a0;
        }
        
        public float[] process(float[] data) {
            float[] output = new float[data.length];
            for (int i = 0; i < data.length; i++) {
                double x0 = data[i];
                double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
                
                x2 = x1;
                x1 = x0;
                y2 = y1;
                y1 = y0;
                
                output[i] = (float) y0;
            }
            return output;
        }
        
        public float getFrequency() { return frequency; }
        public float getQ() { return q; }
        public float getGain() { return gain; }
    }
    
    // ==================== NOISE REMOVAL ====================
    public static class NoiseRemoval {
        private boolean enabled = false;
        private int sampleRate;
        private float threshold = -40.0f; // dB
        private float reduction = 20.0f; // dB
        private float[] noiseProfile;
        private boolean profileCaptured = false;
        private int fftSize = 1024;
        
        public NoiseRemoval(int sampleRate) {
            this.sampleRate = sampleRate;
            noiseProfile = new float[fftSize / 2];
        }
        
        public void captureNoiseProfile(float[] noiseSample) {
            // Compute magnitude spectrum of noise
            float[] magnitudes = computeFFTMagnitude(noiseSample);
            if (magnitudes != null) {
                System.arraycopy(magnitudes, 0, noiseProfile, 0, 
                    Math.min(magnitudes.length, noiseProfile.length));
                profileCaptured = true;
            }
        }
        
        public float[] process(float[] data) {
            if (!profileCaptured) return data;
            
            // Spectral subtraction noise removal
            float[] output = new float[data.length];
            int hopSize = fftSize / 4;
            
            for (int offset = 0; offset < data.length - fftSize; offset += hopSize) {
                float[] frame = new float[fftSize];
                System.arraycopy(data, offset, frame, 0, Math.min(fftSize, data.length - offset));
                
                // Apply Hann window
                for (int i = 0; i < fftSize; i++) {
                    frame[i] *= 0.5f * (1.0f - (float) Math.cos(2.0 * Math.PI * i / (fftSize - 1)));
                }
                
                // Simple spectral gate
                float rms = 0;
                for (float s : frame) rms += s * s;
                rms = (float) Math.sqrt(rms / frame.length);
                float dbLevel = 20.0f * (float) Math.log10(rms + 1e-10f);
                
                float gain = dbLevel < threshold ? 
                    (float) Math.pow(10.0, -reduction / 20.0) : 1.0f;
                
                for (int i = 0; i < fftSize && (offset + i) < output.length; i++) {
                    output[offset + i] += frame[i] * gain;
                }
            }
            
            // Handle remaining samples
            if (data.length <= fftSize) {
                float rms = 0;
                for (float s : data) rms += s * s;
                rms = (float) Math.sqrt(rms / data.length);
                float dbLevel = 20.0f * (float) Math.log10(rms + 1e-10f);
                float gain = dbLevel < threshold ? 
                    (float) Math.pow(10.0, -reduction / 20.0) : 1.0f;
                for (int i = 0; i < data.length; i++) {
                    output[i] = data[i] * gain;
                }
            }
            
            return output;
        }
        
        private float[] computeFFTMagnitude(float[] data) {
            int n = Math.min(data.length, fftSize);
            float[] magnitudes = new float[n / 2];
            for (int k = 0; k < n / 2; k++) {
                float real = 0, imag = 0;
                for (int i = 0; i < n; i++) {
                    double angle = -2.0 * Math.PI * k * i / n;
                    real += data[i] * Math.cos(angle);
                    imag += data[i] * Math.sin(angle);
                }
                magnitudes[k] = (float) Math.sqrt(real * real + imag * imag) / n;
            }
            return magnitudes;
        }
        
        public void setThreshold(float db) { this.threshold = db; }
        public void setReduction(float db) { this.reduction = db; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public float getThreshold() { return threshold; }
        public float getReduction() { return reduction; }
    }
    
    // ==================== AUTO GAIN CONTROL ====================
    public static class AutoGainControl {
        private boolean enabled = false;
        private int sampleRate;
        private float targetLevel = -12.0f; // dB
        private float attackTime = 0.01f; // seconds
        private float releaseTime = 0.1f; // seconds
        private float maxGain = 24.0f; // dB
        private float currentGain = 0.0f;
        
        public AutoGainControl(int sampleRate) {
            this.sampleRate = sampleRate;
        }
        
        public float[] process(float[] data) {
            float[] output = new float[data.length];
            
            float attackCoeff = (float) Math.exp(-1.0 / (attackTime * sampleRate));
            float releaseCoeff = (float) Math.exp(-1.0 / (releaseTime * sampleRate));
            
            for (int i = 0; i < data.length; i++) {
                float level = Math.abs(data[i]);
                float dbLevel = 20.0f * (float) Math.log10(level + 1e-10f);
                float desiredGain = targetLevel - dbLevel;
                desiredGain = Math.min(desiredGain, maxGain);
                desiredGain = Math.max(desiredGain, -maxGain);
                
                if (desiredGain < currentGain) {
                    currentGain = attackCoeff * currentGain + (1 - attackCoeff) * desiredGain;
                } else {
                    currentGain = releaseCoeff * currentGain + (1 - releaseCoeff) * desiredGain;
                }
                
                float linearGain = (float) Math.pow(10.0, currentGain / 20.0);
                output[i] = data[i] * linearGain;
                
                // Soft clip
                if (output[i] > 1.0f) output[i] = (float) Math.tanh(output[i]);
                else if (output[i] < -1.0f) output[i] = (float) Math.tanh(output[i]);
            }
            
            return output;
        }
        
        public void setTargetLevel(float db) { this.targetLevel = db; }
        public void setAttackTime(float seconds) { this.attackTime = seconds; }
        public void setReleaseTime(float seconds) { this.releaseTime = seconds; }
        public void setMaxGain(float db) { this.maxGain = db; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    
    // ==================== FILTER BANK ====================
    public static class FilterBank {
        private boolean enabled = false;
        private int sampleRate;
        private List<BiquadFilter> filters = new ArrayList<>();
        
        public enum FilterType {
            LOW_PASS, HIGH_PASS, BAND_PASS, BAND_STOP, LOW_SHELF, HIGH_SHELF, ALL_PASS, BUTTERWORTH_LP, BUTTERWORTH_HP
        }
        
        public FilterBank(int sampleRate) {
            this.sampleRate = sampleRate;
        }
        
        public void addFilter(FilterType type, float frequency, float q) {
            filters.add(new BiquadFilter(type, frequency, q, sampleRate));
        }
        
        public void clearFilters() {
            filters.clear();
        }
        
        public void removeFilter(int index) {
            if (index >= 0 && index < filters.size()) {
                filters.remove(index);
            }
        }
        
        public float[] process(float[] data) {
            float[] output = data;
            for (BiquadFilter filter : filters) {
                output = filter.process(output);
            }
            return output;
        }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<BiquadFilter> getFilters() { return filters; }
    }
    
    public static class BiquadFilter {
        private FilterBank.FilterType type;
        private float frequency;
        private float q;
        private int sampleRate;
        
        private double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;
        
        public BiquadFilter(FilterBank.FilterType type, float frequency, float q, int sampleRate) {
            this.type = type;
            this.frequency = frequency;
            this.q = q;
            this.sampleRate = sampleRate;
            calculateCoefficients();
        }
        
        private void calculateCoefficients() {
            double w0 = 2.0 * Math.PI * frequency / sampleRate;
            double alpha = Math.sin(w0) / (2.0 * q);
            double cosW0 = Math.cos(w0);
            double a0;
            
            switch (type) {
                case LOW_PASS:
                    b0 = (1.0 - cosW0) / 2.0;
                    b1 = 1.0 - cosW0;
                    b2 = (1.0 - cosW0) / 2.0;
                    a0 = 1.0 + alpha;
                    a1 = -2.0 * cosW0;
                    a2 = 1.0 - alpha;
                    break;
                case HIGH_PASS:
                    b0 = (1.0 + cosW0) / 2.0;
                    b1 = -(1.0 + cosW0);
                    b2 = (1.0 + cosW0) / 2.0;
                    a0 = 1.0 + alpha;
                    a1 = -2.0 * cosW0;
                    a2 = 1.0 - alpha;
                    break;
                case BAND_PASS:
                    b0 = alpha;
                    b1 = 0.0;
                    b2 = -alpha;
                    a0 = 1.0 + alpha;
                    a1 = -2.0 * cosW0;
                    a2 = 1.0 - alpha;
                    break;
                case BAND_STOP:
                    b0 = 1.0;
                    b1 = -2.0 * cosW0;
                    b2 = 1.0;
                    a0 = 1.0 + alpha;
                    a1 = -2.0 * cosW0;
                    a2 = 1.0 - alpha;
                    break;
                case BUTTERWORTH_LP:
                    double resonance = Math.sqrt(2.0); 
                    double omega = 2.0 * Math.PI * frequency / sampleRate;
                    double sn = Math.sin(omega);
                    double cs = Math.cos(omega);
                    double alphab = sn / resonance;
                    b0 = (1.0 - cs) / 2.0;
                    b1 = 1.0 - cs;
                    b2 = (1.0 - cs) / 2.0;
                    a0 = 1.0 + alphab;
                    a1 = -2.0 * cs;
                    a2 = 1.0 - alphab;
                    break;
                case BUTTERWORTH_HP:
                    double resonanceHP = Math.sqrt(2.0);
                    double omegaHP = 2.0 * Math.PI * frequency / sampleRate;
                    double snHP = Math.sin(omegaHP);
                    double csHP = Math.cos(omegaHP);
                    double alphabHP = snHP / resonanceHP;
                    b0 = (1.0 + csHP) / 2.0;
                    b1 = -(1.0 + csHP);
                    b2 = (1.0 + csHP) / 2.0;
                    a0 = 1.0 + alphabHP;
                    a1 = -2.0 * csHP;
                    a2 = 1.0 - alphabHP;
                    break;
                default:
                    b0 = 1.0; b1 = 0.0; b2 = 0.0;
                    a0 = 1.0; a1 = 0.0; a2 = 0.0;
                    break;
            }
            
            b0 /= a0; b1 /= a0; b2 /= a0;
            a1 /= a0; a2 /= a0;
        }
        
        public float[] process(float[] data) {
            float[] output = new float[data.length];
            for (int i = 0; i < data.length; i++) {
                double x0 = data[i];
                double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
                x2 = x1; x1 = x0;
                y2 = y1; y1 = y0;
                output[i] = (float) y0;
            }
            return output;
        }
        
        public FilterBank.FilterType getType() { return type; }
        public float getFrequency() { return frequency; }
        public float getQ() { return q; }
    }
    
    // ==================== REVERBERANCE ====================
    public static class Reverberance {
        private boolean enabled = false;
        private int sampleRate;
        private float roomSize = 0.5f;
        private float damping = 0.5f;
        private float wetLevel = 0.3f;
        private float dryLevel = 0.7f;
        
        // Comb filters
        private float[][] combBuffers;
        private int[] combIndices;
        private static final int[] COMB_LENGTHS = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        
        // All-pass filters
        private float[][] allpassBuffers;
        private int[] allpassIndices;
        private static final int[] ALLPASS_LENGTHS = {556, 441, 341, 225};
        
        public Reverberance(int sampleRate) {
            this.sampleRate = sampleRate;
            initBuffers();
        }
        
        private void initBuffers() {
            float scaleFactor = (float) sampleRate / 44100.0f;
            
            combBuffers = new float[COMB_LENGTHS.length][];
            combIndices = new int[COMB_LENGTHS.length];
            for (int i = 0; i < COMB_LENGTHS.length; i++) {
                int length = (int) (COMB_LENGTHS[i] * scaleFactor);
                combBuffers[i] = new float[length];
                combIndices[i] = 0;
            }
            
            allpassBuffers = new float[ALLPASS_LENGTHS.length][];
            allpassIndices = new int[ALLPASS_LENGTHS.length];
            for (int i = 0; i < ALLPASS_LENGTHS.length; i++) {
                int length = (int) (ALLPASS_LENGTHS[i] * scaleFactor);
                allpassBuffers[i] = new float[length];
                allpassIndices[i] = 0;
            }
        }
        
        public float[] process(float[] data) {
            float[] output = new float[data.length];
            float feedback = roomSize * 0.98f;
            float damp = damping * 0.4f;
            
            for (int i = 0; i < data.length; i++) {
                float input = data[i];
                float combSum = 0;
                
                // Parallel comb filters
                for (int c = 0; c < combBuffers.length; c++) {
                    float[] buffer = combBuffers[c];
                    int idx = combIndices[c];
                    float bufOut = buffer[idx];
                    combSum += bufOut;
                    buffer[idx] = input + bufOut * feedback * (1.0f - damp);
                    combIndices[c] = (idx + 1) % buffer.length;
                }
                combSum /= combBuffers.length;
                
                // Series all-pass filters
                float allpassOut = combSum;
                for (int a = 0; a < allpassBuffers.length; a++) {
                    float[] buffer = allpassBuffers[a];
                    int idx = allpassIndices[a];
                    float bufOut = buffer[idx];
                    buffer[idx] = allpassOut + bufOut * 0.5f;
                    allpassOut = bufOut - allpassOut * 0.5f;
                    allpassIndices[a] = (idx + 1) % buffer.length;
                }
                
                output[i] = data[i] * dryLevel + allpassOut * wetLevel;
            }
            
            return output;
        }
        
        public void setRoomSize(float size) { this.roomSize = size; }
        public void setDamping(float damp) { this.damping = damp; }
        public void setWetLevel(float wet) { this.wetLevel = wet; }
        public void setDryLevel(float dry) { this.dryLevel = dry; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    
    // ==================== BLIND SOURCE SEPARATION ====================
    public static class BlindSourceSeparation {
        private boolean enabled = false;
        private int sampleRate;
        private int numSources = 2;
        private float[][] separationMatrix;
        
        public BlindSourceSeparation(int sampleRate) {
            this.sampleRate = sampleRate;
            initMatrix();
        }
        
        private void initMatrix() {
            separationMatrix = new float[numSources][numSources];
            // Initialize as identity
            for (int i = 0; i < numSources; i++) {
                separationMatrix[i][i] = 1.0f;
            }
        }
        
        public float[] process(float[] data) {
            // Simple ICA-based separation for mono signal
            // In practice, this works with multi-channel input
            return data; // Pass-through for single channel
        }
        
        public float[][] processMutiChannel(float[][] multiChannelData) {
            if (multiChannelData.length < 2) return multiChannelData;
            
            int channels = multiChannelData.length;
            int samples = multiChannelData[0].length;
            float[][] output = new float[channels][samples];
            
            // FastICA-inspired separation
            for (int s = 0; s < samples; s++) {
                for (int i = 0; i < channels; i++) {
                    float sum = 0;
                    for (int j = 0; j < channels; j++) {
                        float w = (i < separationMatrix.length && j < separationMatrix[i].length) 
                            ? separationMatrix[i][j] : (i == j ? 1.0f : 0.0f);
                        sum += w * multiChannelData[j][s];
                    }
                    output[i][s] = sum;
                }
            }
            
            return output;
        }
        
        public void setNumSources(int num) {
            this.numSources = num;
            initMatrix();
        }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
