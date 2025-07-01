package com.example.wi_map.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.wi_map.R;
import com.example.wi_map.models.WifiEntry;
import com.example.wi_map.interfaces.INetworkClickListener;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

public class NetworkAdapter
        extends RecyclerView.Adapter<NetworkAdapter.ViewHolder> {

    private final List<WifiEntry> data;
    private final INetworkClickListener listener;

    public NetworkAdapter(List<WifiEntry> data, INetworkClickListener listener) {
        this.data = data;
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialTextView ssid;
        MaterialTextView distance;
        MaterialTextView rssi;

        public ViewHolder(View itemView) {
            super(itemView);
            ssid     = itemView.findViewById(R.id.tv_ssid);
            distance = itemView.findViewById(R.id.tv_distance);
            rssi     = itemView.findViewById(R.id.tv_rssi);
        }

        public void bind(WifiEntry entry, INetworkClickListener l) {
            ssid.setText(entry.ssid);
            distance.setText(Math.round(entry.distanceM) + " m");
            rssi.setText(entry.level + " dBm");
            itemView.setOnClickListener(v -> l.onNetworkClick(entry));
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_network, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.bind(data.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
