package com.ultraaudio.recorder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ultraaudio.recorder.R;
import com.ultraaudio.recorder.audio.AudioEngine;
import com.ultraaudio.recorder.audio.AudioRecorder;
import com.ultraaudio.recorder.dsp.FFTAnalyzer;
import com.ultraaudio.recorder.visualization.SpectrumView;
import com.ultraaudio.recorder.visualization.WaveformView;

public class RecorderFragment extends Fragment {
    private WaveformView waveformView;
    private SpectrumView spectrumView;
    private Spinner formatSpinner, sampleRateSpinner, channelsSpinner;
    private SeekBar gainSeekbar;
    private TextView gainValue;
    
    private AudioEngine.AudioDataListener dataListener;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recorder, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        waveformView = view.findViewById(R.id.waveform_view);
        spectrumView = view.findViewById(R.id.spectrum_view);
        formatSpinner = view.findViewById(R.id.spinner_format);
        sampleRateSpinner = view.findViewById(R.id.spinner_sample_rate);
        channelsSpinner = view.findViewById(R.id.spinner_channels);
        gainSeekbar = view.findViewById(R.id.seekbar_gain);
        gainValue = view.findViewById(R.id.tv_gain_value);
        
        setupSpinners();
        setupGainControl();
        setupAudioListener();
    }
    
    private void setupSpinners() {
        // Format spinner
        String[] formats = {"WAV PCM 16-bit", "WAV PCM 24-bit", "WAV PCM 32-bit", 
            "WAV Float 32-bit", "WAV Float 64-bit", "WAV A-Law", "WAV U-Law",
            "FLAC", "M4A (AAC)", "OGG/Vorbis", "Speex"};
        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_item, formats);
        formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(formatAdapter);
        formatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AudioRecorder.Format[] formatValues = {
                    AudioRecorder.Format.WAV_PCM_16, AudioRecorder.Format.WAV_PCM_24,
                    AudioRecorder.Format.WAV_PCM_32, AudioRecorder.Format.WAV_FLOAT_32,
                    AudioRecorder.Format.WAV_FLOAT_64, AudioRecorder.Format.WAV_ALAW,
                    AudioRecorder.Format.WAV_ULAW, AudioRecorder.Format.FLAC,
                    AudioRecorder.Format.M4A_AAC, AudioRecorder.Format.OGG_VORBIS,
                    AudioRecorder.Format.SPEEX
                };
                if (position < formatValues.length) {
                    ((MainActivity) requireActivity()).setSelectedFormat(formatValues[position]);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        // Sample rate spinner
        String[] rates = {"8000 Hz", "11025 Hz", "16000 Hz", "22050 Hz", "44100 Hz", 
            "48000 Hz", "88200 Hz", "96000 Hz"};
        ArrayAdapter<String> rateAdapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_item, rates);
        rateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sampleRateSpinner.setAdapter(rateAdapter);
        sampleRateSpinner.setSelection(5); // 48000 Hz default
        sampleRateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int[] rateValues = {8000, 11025, 16000, 22050, 44100, 48000, 88200, 96000};
                if (position < rateValues.length) {
                    ((MainActivity) requireActivity()).setSelectedSampleRate(rateValues[position]);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        // Channels spinner
        String[] channelOptions = {"Mono", "Stereo"};
        ArrayAdapter<String> channelAdapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_item, channelOptions);
        channelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        channelsSpinner.setAdapter(channelAdapter);
        channelsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((MainActivity) requireActivity()).setSelectedChannels(position + 1);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
    
    private void setupGainControl() {
        gainSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float gain = progress / 10.0f;
                gainValue.setText(String.format("x%.1f", gain));
                ((MainActivity) requireActivity()).getAudioEngine().setMasterGain(gain);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
    
    private void setupAudioListener() {
        MainActivity activity = (MainActivity) requireActivity();
        
        dataListener = (data, sampleRate, channels) -> {
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (waveformView != null) {
                    waveformView.updateData(data);
                }
                
                FFTAnalyzer analyzer = activity.getFFTAnalyzer();
                if (spectrumView != null && analyzer != null) {
                    spectrumView.updateData(analyzer.getMagnitudes(), analyzer.getFrequencies());
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
