package com.ultraaudio.recorder.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ultraaudio.recorder.R;
import com.ultraaudio.recorder.audio.MicrophoneArrayManager;
import com.ultraaudio.recorder.visualization.TopologicalMapView;

import java.util.ArrayList;
import java.util.List;

public class ArrayFragment extends Fragment {
    private TopologicalMapView topologicalMap;
    private MicrophoneArrayManager arrayManager;
    private RecyclerView rvMics;
    private View playbackControls;
    private SeekBar seekbarPlayback;
    private ImageButton btnPlayHistory;
    
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private boolean isPlayingHistory = false;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_array, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        topologicalMap = view.findViewById(R.id.topological_map);
        arrayManager = ((MainActivity) requireActivity()).getArrayManager();
        rvMics = view.findViewById(R.id.rv_array_mics);
        playbackControls = view.findViewById(R.id.playback_controls);
        seekbarPlayback = view.findViewById(R.id.seekbar_playback);
        btnPlayHistory = view.findViewById(R.id.btn_play_history);
        
        setupMap();
        setupControls(view);
        startLiveUpdate();
    }
    
    private void setupMap() {
        List<TopologicalMapView.MicPosition> positions = new ArrayList<>();
        // Mock positions for demo/initialization
        positions.add(new TopologicalMapView.MicPosition(0.5f, 0.2f, 0, "Built-in Top"));
        positions.add(new TopologicalMapView.MicPosition(0.5f, 0.8f, 0, "Built-in Bottom"));
        positions.add(new TopologicalMapView.MicPosition(0.2f, 0.5f, 0, "USB Mic L"));
        positions.add(new TopologicalMapView.MicPosition(0.8f, 0.5f, 0, "USB Mic R"));
        
        topologicalMap.setMicPositions(positions);
    }
    
    private void setupControls(View view) {
        view.findViewById(R.id.btn_start_array).setOnClickListener(v -> {
            arrayManager.startArrayCapture();
            Toast.makeText(getContext(), "Array Capture Started", Toast.LENGTH_SHORT).show();
        });
        
        view.findViewById(R.id.btn_2d_mode).setOnClickListener(v -> topologicalMap.set3DMode(false));
        view.findViewById(R.id.btn_3d_mode).setOnClickListener(v -> topologicalMap.set3DMode(true));
        
        view.findViewById(R.id.btn_playback_mode).setOnClickListener(v -> {
            if (playbackControls.getVisibility() == View.VISIBLE) {
                playbackControls.setVisibility(View.GONE);
                topologicalMap.setPlaybackIndex(-1);
            } else {
                playbackControls.setVisibility(View.VISIBLE);
                seekbarPlayback.setMax(Math.max(0, topologicalMap.getHistoryLength() - 1));
            }
        });
        
        seekbarPlayback.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    topologicalMap.setPlaybackIndex(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { isPlayingHistory = false; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        btnPlayHistory.setOnClickListener(v -> {
            isPlayingHistory = !isPlayingHistory;
            if (isPlayingHistory) {
                btnPlayHistory.setImageResource(android.R.drawable.ic_media_pause);
                playNextFrame();
            } else {
                btnPlayHistory.setImageResource(android.R.drawable.ic_media_play);
            }
        });
        
        view.findViewById(R.id.btn_calibrate).setOnClickListener(v -> {
            Toast.makeText(getContext(), "Calibrating microphones...", Toast.LENGTH_SHORT).show();
            arrayManager.calibrate();
        });
        
        view.findViewById(R.id.btn_export_heatmap).setOnClickListener(v -> {
            Toast.makeText(getContext(), "Exporting spatial data to CAB format...", Toast.LENGTH_SHORT).show();
            // Implementation would save to file
        });
    }
    
    private void playNextFrame() {
        if (!isPlayingHistory) return;
        
        int current = seekbarPlayback.getProgress();
        if (current < topologicalMap.getHistoryLength() - 1) {
            seekbarPlayback.setProgress(current + 1);
            topologicalMap.setPlaybackIndex(current + 1);
            updateHandler.postDelayed(this::playNextFrame, 100); // 10fps playback
        } else {
            isPlayingHistory = false;
            btnPlayHistory.setImageResource(android.R.drawable.ic_media_play);
        }
    }
    
    private void startLiveUpdate() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPlayingHistory && isAdded() && topologicalMap != null) {
                    float[] levels = arrayManager.getLatestLevels();
                    float[] directions = arrayManager.getLatestDirections();
                    if (levels != null) {
                        topologicalMap.updateLevels(levels, directions);
                    }
                }
                updateHandler.postDelayed(this, 100);
            }
        };
        updateHandler.post(updateRunnable);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        updateHandler.removeCallbacks(updateRunnable);
    }
}
