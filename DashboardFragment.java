package com.ultraaudio.recorder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ultraaudio.recorder.R;
import com.ultraaudio.recorder.audio.AudioEngine;
import com.ultraaudio.recorder.dsp.FFTAnalyzer;
import com.ultraaudio.recorder.visualization.SpectrogramView;
import com.ultraaudio.recorder.visualization.SpectrumView;
import com.ultraaudio.recorder.visualization.WaveformView;

public class DashboardFragment extends Fragment {
    private GridLayout gridLayout;
    private WaveformView waveformView;
    private SpectrumView spectrumView;
    private SpectrogramView spectrogramView;
    private SpectrogramView spectrogram3DView;
    
    private AudioEngine.AudioDataListener dataListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        gridLayout = view.findViewById(R.id.dashboard_grid);
        
        view.findViewById(R.id.btn_grid_2x2).setOnClickListener(v -> setupGrid(2));
        view.findViewById(R.id.btn_grid_4x4).setOnClickListener(v -> setupGrid(4));
        
        setupGrid(2); // Default 2x2
        return view;
    }

    private void setupGrid(int size) {
        gridLayout.removeAllViews();
        gridLayout.setColumnCount(size);
        gridLayout.setRowCount(size);

        waveformView = new WaveformView(getContext(), null);
        spectrumView = new SpectrumView(getContext(), null);
        spectrogramView = new SpectrogramView(getContext(), null);
        spectrogram3DView = new SpectrogramView(getContext(), null);
        spectrogram3DView.set3DMode(true);

        addViewToGrid(waveformView);
        addViewToGrid(spectrumView);
        addViewToGrid(spectrogramView);
        addViewToGrid(spectrogram3DView);
    }

    private void addViewToGrid(View view) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = 0;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(4, 4, 4, 4);
        view.setLayoutParams(params);
        view.setBackgroundColor(0xFF161B22);
        gridLayout.addView(view);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupAudioListener();
    }

    private void setupAudioListener() {
        MainActivity activity = (MainActivity) requireActivity();
        dataListener = (data, sampleRate, channels) -> {
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                FFTAnalyzer analyzer = activity.getFFTAnalyzer();
                if (waveformView != null) waveformView.updateData(data);
                if (spectrumView != null) spectrumView.updateData(analyzer.getMagnitudes(), analyzer.getFrequencies());
                if (spectrogramView != null) spectrogramView.updateColumn(analyzer.getMagnitudes());
                if (spectrogram3DView != null) spectrogram3DView.updateColumn(analyzer.getMagnitudes());
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
