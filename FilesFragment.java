package com.ultraaudio.recorder.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ultraaudio.recorder.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FilesFragment extends Fragment {
    private RecyclerView rvFiles;
    private TextView tvPath;
    private FileAdapter adapter;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_files, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        rvFiles = view.findViewById(R.id.rv_files);
        tvPath = view.findViewById(R.id.tv_storage_path);
        
        rvFiles.setLayoutManager(new LinearLayoutManager(getContext()));
        refreshFileList();
    }
    
    private void refreshFileList() {
        String dirPath = ((MainActivity) requireActivity()).getRecordingDir();
        tvPath.setText(dirPath);
        
        File dir = new File(dirPath);
        File[] files = dir.listFiles();
        List<File> fileList = new ArrayList<>();
        if (files != null) {
            fileList.addAll(Arrays.asList(files));
            Collections.sort(fileList, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        }
        
        adapter = new FileAdapter(fileList);
        rvFiles.setAdapter(adapter);
    }
    
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private List<File> files;
        
        public FileAdapter(List<File> files) { this.files = files; }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File f = files.get(position);
            holder.text1.setText(f.getName());
            holder.text1.setTextColor(0xFFFFFFFF);
            
            long size = f.length() / 1024;
            String info = (size > 1024 ? (size/1024 + " MB") : (size + " KB")) + " | " + 
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(new java.util.Date(f.lastModified()));
            holder.text2.setText(info);
            holder.text2.setTextColor(0xFFAAAAAA);
            
            holder.itemView.setOnClickListener(v -> openFile(f));
        }
        
        @Override
        public int getItemCount() { return files.size(); }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
            }
        }
    }
    
    private void openFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "audio/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "No app found to open this file", Toast.LENGTH_SHORT).show();
        }
    }
}
