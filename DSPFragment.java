package com.ultraaudio.recorder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ultraaudio.recorder.R;
import com.ultraaudio.recorder.dsp.DSPProcessor;

public class DSPFragment extends Fragment {
    private DSPProcessor dspProcessor;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dsp, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        dspProcessor = ((MainActivity) requireActivity()).getDSPProcessor();
        
        // Master DSP switch
        Switch masterSwitch = view.findViewById(R.id.switch_dsp_master);
        masterSwitch.setChecked(dspProcessor.isEnabled());
        masterSwitch.setOnCheckedChangeListener((btn, checked) -> dspProcessor.setEnabled(checked));
        
        // EQ
        Switch eqSwitch = view.findViewById(R.id.switch_eq);
        eqSwitch.setOnCheckedChangeListener((btn, checked) -> 
            dspProcessor.getEqualizer().setEnabled(checked));
        
        Spinner eqBandsSpinner = view.findViewById(R.id.spinner_eq_bands);
        String[] bandCounts = {"4", "6", "8", "10", "15", "20", "31", "60"};
        ArrayAdapter<String> bandAdapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_item, bandCounts);
        bandAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        eqBandsSpinner.setAdapter(bandAdapter);
        eqBandsSpinner.setSelection(3); // 10 bands default
        eqBandsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                int[] counts = {4, 6, 8, 10, 15, 20, 31, 60};
                dspProcessor.getEqualizer().setBandCount(counts[pos]);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        // Noise removal
        Switch noiseSwitch = view.findViewById(R.id.switch_noise);
        noiseSwitch.setOnCheckedChangeListener((btn, checked) -> 
            dspProcessor.getNoiseRemoval().setEnabled(checked));
        
        SeekBar noiseThreshold = view.findViewById(R.id.seekbar_noise_threshold);
        TextView tvNoiseThreshold = view.findViewById(R.id.tv_noise_threshold);
        noiseThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float db = -progress;
                tvNoiseThreshold.setText(String.format("%.0fdB", db));
                dspProcessor.getNoiseRemoval().setThreshold(db);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        SeekBar noiseReduction = view.findViewById(R.id.seekbar_noise_reduction);
        TextView tvNoiseReduction = view.findViewById(R.id.tv_noise_reduction);
        noiseReduction.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvNoiseReduction.setText(String.format("%ddB", progress));
                dspProcessor.getNoiseRemoval().setReduction(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        view.findViewById(R.id.btn_capture_noise).setOnClickListener(v -> {
            // Capture noise profile from current audio
            // In a real implementation, this would capture a few seconds of ambient noise
        });
        
        // AGC
        Switch agcSwitch = view.findViewById(R.id.switch_agc);
        agcSwitch.setOnCheckedChangeListener((btn, checked) -> 
            dspProcessor.getAgc().setEnabled(checked));
        
        SeekBar agcTarget = view.findViewById(R.id.seekbar_agc_target);
        TextView tvAgcTarget = view.findViewById(R.id.tv_agc_target);
        agcTarget.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float db = -progress;
                tvAgcTarget.setText(String.format("%.0fdB", db));
                dspProcessor.getAgc().setTargetLevel(db);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Filters
        Switch filtersSwitch = view.findViewById(R.id.switch_filters);
        filtersSwitch.setOnCheckedChangeListener((btn, checked) -> 
            dspProcessor.getFilterBank().setEnabled(checked));
        
        view.findViewById(R.id.btn_add_filter).setOnClickListener(v -> {
            // Add default high-pass filter at 80Hz
            dspProcessor.getFilterBank().addFilter(
                DSPProcessor.FilterBank.FilterType.HIGH_PASS, 80f, 0.707f);
        });
        
        // Reverb
        Switch reverbSwitch = view.findViewById(R.id.switch_reverb);
        reverbSwitch.setOnCheckedChangeListener((btn, checked) -> 
            dspProcessor.getReverb().setEnabled(checked));
        
        SeekBar reverbRoom = view.findViewById(R.id.seekbar_reverb_room);
        reverbRoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                dspProcessor.getReverb().setRoomSize(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        SeekBar reverbWet = view.findViewById(R.id.seekbar_reverb_wet);
        reverbWet.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float wet = progress / 100f;
                dspProcessor.getReverb().setWetLevel(wet);
                dspProcessor.getReverb().setDryLevel(1.0f - wet);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // BSS
        Switch bssSwitch = view.findViewById(R.id.switch_bss);
        bssSwitch.setOnCheckedChangeListener((btn, checked) -> 
            dspProcessor.getBss().setEnabled(checked));
    }
}
