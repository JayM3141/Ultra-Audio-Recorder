package com.ultraaudio.recorder.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ultraaudio.recorder.R;
import com.ultraaudio.recorder.audio.MicrophoneArrayManager;
import com.ultraaudio.recorder.audio.MicrophoneInput;
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
    private MicAdapter micAdapter;
    private MicrophoneArrayManager.ArrayDataListener arrayDataListener;
    
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
        setupMicList();
        setupArrayListener();
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

        view.findViewById(R.id.btn_configure_array).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ArrayConfigActivity.class);
            startActivity(intent);
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

        view.findViewById(R.id.btn_pause_history).setOnClickListener(v -> {
            isPlayingHistory = false;
            btnPlayHistory.setImageResource(android.R.drawable.ic_media_play);
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
    
    private void setupMicList() {
        rvMics.setLayoutManager(new LinearLayoutManager(getContext()));
        micAdapter = new MicAdapter();
        rvMics.setAdapter(micAdapter);
    }
    
    private void setupArrayListener() {
        arrayDataListener = new MicrophoneArrayManager.ArrayDataListener() {
            @Override
            public void onArrayData(java.util.Map<Integer, float[]> allMicData, int sampleRate) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (micAdapter != null) {
                        micAdapter.update(arrayManager.getMicrophones());
                    }
                    rebuildMicPositions();
                });
            }

            @Override
            public void onSpatialUpdate(float[] directions, float[] levels) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (levels != null && levels.length > 0) {
                        topologicalMap.updateLevels(levels, directions);
                        seekbarPlayback.setMax(Math.max(0, topologicalMap.getHistoryLength() - 1));
                    }
                });
            }
        };
        arrayManager.addListener(arrayDataListener);
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
        if (arrayManager != null && arrayDataListener != null) {
            arrayManager.removeListener(arrayDataListener);
        }
    }

    private void rebuildMicPositions() {
        List<MicrophoneInput> microphones = arrayManager.getMicrophones();
        List<TopologicalMapView.MicPosition> positions = new ArrayList<>();
        int count = microphones.size();
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * i / Math.max(1, count);
            float x = 0.5f + (float) (Math.cos(angle) * 0.3f);
            float y = 0.5f + (float) (Math.sin(angle) * 0.3f);
            positions.add(new TopologicalMapView.MicPosition(x, y, 0, microphones.get(i).getLabel()));
        }
        if (!positions.isEmpty()) {
            topologicalMap.setMicPositions(positions);
        }
    }

    private class MicAdapter extends RecyclerView.Adapter<MicAdapter.MicViewHolder> {
        private final List<MicrophoneInput> microphones = new ArrayList<>();

        @NonNull
        @Override
        public MicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_array_mic, parent, false);
            return new MicViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MicViewHolder holder, int position) {
            MicrophoneInput mic = microphones.get(position);
            holder.label.setText(mic.getLabel());
            holder.status.setText(mic.isActive() ? "Active" : "Idle");
        }

        @Override
        public int getItemCount() {
            return microphones.size();
        }

        public void update(List<MicrophoneInput> newMics) {
            microphones.clear();
            microphones.addAll(newMics);
            notifyDataSetChanged();
        }

        class MicViewHolder extends RecyclerView.ViewHolder {
            TextView label;
            TextView status;

            MicViewHolder(@NonNull View itemView) {
                super(itemView);
                label = itemView.findViewById(R.id.tv_mic_label);
                status = itemView.findViewById(R.id.tv_mic_status);
            }
        }
    }
}
