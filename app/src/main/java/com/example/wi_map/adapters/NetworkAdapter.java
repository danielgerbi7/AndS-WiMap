package com.example.wi_map.adapters;

import android.net.wifi.ScanResult;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.wi_map.R;
import com.example.wi_map.interfaces.INetworkClickListener;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

public class NetworkAdapter
        extends RecyclerView.Adapter<NetworkAdapter.ViewHolder> {

    private final List<ScanResult> data;
    private final INetworkClickListener listener;

    public NetworkAdapter(List<ScanResult> data, INetworkClickListener listener) {
        this.data = data;
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialTextView ssid, bssid, rssi;

        public ViewHolder(View itemView) {
            super(itemView);
            ssid  = itemView.findViewById(R.id.tv_ssid);
            bssid = itemView.findViewById(R.id.tv_bssid);
            rssi  = itemView.findViewById(R.id.tv_rssi);
        }

        public void bind(ScanResult net, INetworkClickListener l) {
            ssid.setText(net.SSID);
            bssid.setText(net.BSSID);
            rssi.setText(net.level + " dBm");
            itemView.setOnClickListener(v -> l.onNetworkClick(net));
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
