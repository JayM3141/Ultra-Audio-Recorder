package com.ultraaudio.recorder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ultraaudio.recorder.R;
import com.ultraaudio.recorder.audio.AudioEngine;
import com.ultraaudio.recorder.dsp.FFTAnalyzer;
import com.ultraaudio.recorder.visualization.SpectrumView;
import com.ultraaudio.recorder.visualization.SpectrogramView;
import com.ultraaudio.recorder.visualization.WaveformView;

public class AnalysisFragment extends Fragment {
    private SpectrogramView spectrogramView;
    private SpectrumView spectrumView;
    private WaveformView waveformView;
    
    private AudioEngine.AudioDataListener dataListener;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_analysis, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        spectrogramView = view.findViewById(R.id.spectrogram_view);
        spectrumView = view.findViewById(R.id.analysis_spectrum_view);
        waveformView = view.findViewById(R.id.analysis_waveform_view);
        
        // View mode buttons
        view.findViewById(R.id.btn_fft_view).setOnClickListener(v -> {
            spectrumView.setVisibility(View.VISIBLE);
            spectrogramView.setVisibility(View.GONE);
            waveformView.setVisibility(View.GONE);
        });
        
        view.findViewById(R.id.btn_spectrogram_view).setOnClickListener(v -> {
            spectrumView.setVisibility(View.GONE);
            spectrogramView.setVisibility(View.VISIBLE);
            waveformView.setVisibility(View.GONE);
        });
        
        view.findViewById(R.id.btn_oscilloscope_view).setOnClickListener(v -> {
            spectrumView.setVisibility(View.GONE);
            spectrogramView.setVisibility(View.GONE);
            waveformView.setVisibility(View.VISIBLE);
        });
        
        view.findViewById(R.id.btn_3d_view).setOnClickListener(v -> {
            spectrogramView.setVisibility(View.VISIBLE);
            spectrumView.setVisibility(View.GONE);
            waveformView.setVisibility(View.GONE);
            spectrogramView.set3DMode(true);
        });
        
        setupAudioListener();
    }
    
    private void setupAudioListener() {
        MainActivity activity = (MainActivity) requireActivity();
        
        dataListener = (data, sampleRate, channels) -> {
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                FFTAnalyzer analyzer = activity.getFFTAnalyzer();
                
                if (waveformView != null && waveformView.getVisibility() == View.VISIBLE) {
                    waveformView.updateData(data);
                }
                
                if (spectrumView != null && spectrumView.getVisibility() == View.VISIBLE) {
                    spectrumView.updateData(analyzer.getMagnitudes(), analyzer.getFrequencies());
                }
                
                if (spectrogramView != null && spectrogramView.getVisibility() == View.VISIBLE) {
                    spectrogramView.updateColumn(analyzer.getMagnitudes());
                }
            });
        };
        
        activity.getAudioEngine().addDataListener(dataListener);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() != null) {
            ((MainActivity) getActivity()).getAudioEngine().removeDataListener(dataListener);
        }
    }
}
