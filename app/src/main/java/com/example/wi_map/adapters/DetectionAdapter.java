package com.example.wi_map.adapters;

import android.annotation.SuppressLint;
import android.net.wifi.ScanResult;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wi_map.R;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

public class DetectionAdapter extends RecyclerView.Adapter<DetectionAdapter.ViewHolder> {

    private final List<ScanResult> data;

    public DetectionAdapter(List<ScanResult> data) {
        this.data = data;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_network, parent, false);
        return new ViewHolder(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScanResult sr = data.get(position);
        holder.ssid.setText(sr.SSID);
        holder.rssi.setText(sr.level + " dBm");
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialTextView ssid, rssi;

        ViewHolder(View itemView) {
            super(itemView);
            ssid = itemView.findViewById(R.id.tv_ssid);
            rssi = itemView.findViewById(R.id.tv_rssi);
        }
    }
}
