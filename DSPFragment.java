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
    private RecyclerView rvEqBands;
    private RecyclerView rvFilters;
    private EqBandAdapter eqBandAdapter;
    private FilterAdapter filterAdapter;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dsp, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        dspProcessor = ((MainActivity) requireActivity()).getDSPProcessor();
        rvEqBands = view.findViewById(R.id.rv_eq_bands);
        rvFilters = view.findViewById(R.id.rv_filters);
        eqBandAdapter = new EqBandAdapter();
        filterAdapter = new FilterAdapter();
        rvEqBands.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvEqBands.setAdapter(eqBandAdapter);
        rvFilters.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvFilters.setAdapter(filterAdapter);
        
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
                if (eqBandAdapter != null) {
                    eqBandAdapter.notifyDataSetChanged();
                }
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
            // Add a Butterworth low-pass filter at 1000 Hz by default
            dspProcessor.getFilterBank().addFilter(
                DSPProcessor.FilterBank.FilterType.BUTTERWORTH_LP, 1000f, 0.707f);
            if (filterAdapter != null) {
                filterAdapter.notifyDataSetChanged();
            }
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

    private class EqBandAdapter extends RecyclerView.Adapter<EqBandAdapter.BandViewHolder> {
        @NonNull
        @Override
        public BandViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_eq_band, parent, false);
            return new BandViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BandViewHolder holder, int position) {
            DSPProcessor.EQBand band = dspProcessor.getEqualizer().getBands().get(position);
            holder.label.setText(String.format("Band %d", position + 1));
            holder.details.setText(String.format("%.0f Hz · Q %.2f", band.getFrequency(), band.getQ()));
            holder.gainValue.setText(String.format("%.1fdB", band.getGain()));
            holder.gainSeekBar.setMax(48);
            holder.gainSeekBar.setProgress((int) (band.getGain() + 24));
            holder.gainSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float gain = progress - 24;
                    band.setParameters(band.getFrequency(), band.getQ(), gain);
                    holder.gainValue.setText(String.format("%.1fdB", gain));
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        @Override
        public int getItemCount() {
            return dspProcessor.getEqualizer().getBands().size();
        }

        class BandViewHolder extends RecyclerView.ViewHolder {
            TextView label;
            TextView details;
            TextView gainValue;
            SeekBar gainSeekBar;

            BandViewHolder(@NonNull View itemView) {
                super(itemView);
                label = itemView.findViewById(R.id.tv_eq_band_label);
                details = itemView.findViewById(R.id.tv_eq_band_details);
                gainValue = itemView.findViewById(R.id.tv_eq_band_gain);
                gainSeekBar = itemView.findViewById(R.id.seekbar_eq_band_gain);
            }
        }
    }

    private class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.FilterViewHolder> {
        @NonNull
        @Override
        public FilterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_filter, parent, false);
            return new FilterViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FilterViewHolder holder, int position) {
            DSPProcessor.BiquadFilter filter = dspProcessor.getFilterBank().getFilters().get(position);
            holder.title.setText(filter.getType().name().replace('_', ' '));
            holder.details.setText(String.format("%.0f Hz · Q %.2f", filter.getFrequency(), filter.getQ()));
            holder.removeButton.setOnClickListener(v -> {
                dspProcessor.getFilterBank().removeFilter(position);
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return dspProcessor.getFilterBank().getFilters().size();
        }

        class FilterViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView details;
            View removeButton;

            FilterViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tv_filter_label);
                details = itemView.findViewById(R.id.tv_filter_details);
                removeButton = itemView.findViewById(R.id.btn_remove_filter);
            }
        }
    }
}
