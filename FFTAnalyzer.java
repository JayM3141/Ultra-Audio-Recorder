package com.ultraaudio.recorder.dsp;

/**
 * Fast Fourier Transform analyzer for real-time audio analysis.
 * Provides FFT magnitude/phase, spectrogram data, and frequency analysis.
 */
public class FFTAnalyzer {
    private int fftSize;
    private float[] window;
    private float[] magnitudes;
    private float[] phases;
    private float[] frequencies;
    private int sampleRate;
    
    // Spectrogram history
    private float[][] spectrogramHistory;
    private int spectrogramIndex = 0;
    private int spectrogramLength = 256; // frames of history
    
    public FFTAnalyzer(int fftSize, int sampleRate) {
        this.fftSize = fftSize;
        this.sampleRate = sampleRate;
        this.magnitudes = new float[fftSize / 2];
        this.phases = new float[fftSize / 2];
        this.frequencies = new float[fftSize / 2];
        this.window = createHannWindow(fftSize);
        this.spectrogramHistory = new float[spectrogramLength][fftSize / 2];
        
        // Pre-compute frequency bins
        for (int i = 0; i < fftSize / 2; i++) {
            frequencies[i] = (float) i * sampleRate / fftSize;
        }
    }
    
    private float[] createHannWindow(int size) {
        float[] w = new float[size];
        for (int i = 0; i < size; i++) {
            w[i] = 0.5f * (1.0f - (float) Math.cos(2.0 * Math.PI * i / (size - 1)));
        }
        return w;
    }
    
    /**
     * Compute FFT of input data and update magnitudes/phases.
     */
    public void analyze(float[] data) {
        int n = Math.min(data.length, fftSize);
        float[] real = new float[fftSize];
        float[] imag = new float[fftSize];
        
        // Apply window
        for (int i = 0; i < n; i++) {
            real[i] = data[i] * window[i];
        }
        
        // Cooley-Tukey FFT
        fft(real, imag, fftSize);
        
        // Compute magnitudes and phases
        for (int i = 0; i < fftSize / 2; i++) {
            magnitudes[i] = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]) * 2.0f / fftSize;
            phases[i] = (float) Math.atan2(imag[i], real[i]);
        }
        
        // Update spectrogram history
        System.arraycopy(magnitudes, 0, spectrogramHistory[spectrogramIndex], 0, fftSize / 2);
        spectrogramIndex = (spectrogramIndex + 1) % spectrogramLength;
    }
    
    private void fft(float[] real, float[] imag, int n) {
        // Bit reversal
        int j = 0;
        for (int i = 0; i < n; i++) {
            if (j > i) {
                float tempR = real[j]; real[j] = real[i]; real[i] = tempR;
                float tempI = imag[j]; imag[j] = imag[i]; imag[i] = tempI;
            }
            int m = n >> 1;
            while (m >= 1 && j >= m) {
                j -= m;
                m >>= 1;
            }
            j += m;
        }
        
        // FFT butterfly
        for (int step = 2; step <= n; step <<= 1) {
            int halfStep = step >> 1;
            double angle = -2.0 * Math.PI / step;
            
            for (int group = 0; group < n; group += step) {
                for (int pair = 0; pair < halfStep; pair++) {
                    double w = angle * pair;
                    float wr = (float) Math.cos(w);
                    float wi = (float) Math.sin(w);
                    
                    int idx1 = group + pair;
                    int idx2 = group + pair + halfStep;
                    
                    float tr = wr * real[idx2] - wi * imag[idx2];
                    float ti = wr * imag[idx2] + wi * real[idx2];
                    
                    real[idx2] = real[idx1] - tr;
                    imag[idx2] = imag[idx1] - ti;
                    real[idx1] += tr;
                    imag[idx1] += ti;
                }
            }
        }
    }
    
    public float[] getMagnitudes() { return magnitudes; }
    public float[] getMagnitudesDB() {
        float[] db = new float[magnitudes.length];
        for (int i = 0; i < magnitudes.length; i++) {
            db[i] = 20.0f * (float) Math.log10(magnitudes[i] + 1e-10f);
        }
        return db;
    }
    public float[] getPhases() { return phases; }
    public float[] getFrequencies() { return frequencies; }
    public float[][] getSpectrogramHistory() { return spectrogramHistory; }
    public int getSpectrogramIndex() { return spectrogramIndex; }
    public int getFFTSize() { return fftSize; }
    public int getSampleRate() { return sampleRate; }
    
    public float getPeakFrequency() {
        float maxMag = 0;
        int maxIdx = 0;
        for (int i = 1; i < magnitudes.length; i++) {
            if (magnitudes[i] > maxMag) {
                maxMag = magnitudes[i];
                maxIdx = i;
            }
        }
        return frequencies[maxIdx];
    }
    
    public float getRMS() {
        float sum = 0;
        for (float m : magnitudes) sum += m * m;
        return (float) Math.sqrt(sum / magnitudes.length);
    }
    
    public float getDBSPL() {
        return 20.0f * (float) Math.log10(getRMS() + 1e-10f) + 94.0f;
    }
    
    public float getDBA() {
        // A-weighting approximation
        float weightedSum = 0;
        for (int i = 0; i < magnitudes.length; i++) {
            float f = frequencies[i];
            if (f < 1) continue;
            // A-weighting formula
            double f2 = f * f;
            double ra = (f2 * f2 * 148693636.0) / 
                ((f2 + 424.36) * Math.sqrt((f2 + 11599.29) * (f2 + 544496.41)) * (f2 + 148693636.0));
            float weight = (float) (20.0 * Math.log10(ra) + 2.0);
            float weightedMag = magnitudes[i] * (float) Math.pow(10.0, weight / 20.0);
            weightedSum += weightedMag * weightedMag;
        }
        return 20.0f * (float) Math.log10((float) Math.sqrt(weightedSum / magnitudes.length) + 1e-10f) + 94.0f;
    }
    
    public float getSones() {
        float phon = getDBA(); // Simplified: use dBA as approximation of phon
        if (phon < 40) {
            return (float) Math.pow(phon / 40.0, 2.642);
        }
        return (float) Math.pow(2.0, (phon - 40.0) / 10.0);
    }
}
