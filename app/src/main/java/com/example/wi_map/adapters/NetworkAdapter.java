package com.example.wi_map.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wi_map.R;
import com.example.wi_map.models.WifiEntry;
import com.example.wi_map.interfaces.INetworkClickListener;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

public class NetworkAdapter extends RecyclerView.Adapter<NetworkAdapter.ViewHolder> {

    private final List<WifiEntry> data;
    private final INetworkClickListener listener;
    private String distanceUnit;

    public NetworkAdapter(List<WifiEntry> data, INetworkClickListener listener, String distanceUnit) {
        this.data = data;
        this.listener = listener;
        this.distanceUnit = distanceUnit;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialTextView ssid;
        MaterialTextView distance;
        MaterialTextView rssi;

        public ViewHolder(View itemView) {
            super(itemView);
            ssid = itemView.findViewById(R.id.tv_ssid);
            distance = itemView.findViewById(R.id.tv_distance);
            rssi = itemView.findViewById(R.id.tv_rssi);
        }

        @SuppressLint("SetTextI18n")
        public void bind(WifiEntry net, INetworkClickListener l, String distanceUnit) {
            ssid.setText(net.ssid);
            distance.setText(Math.round(net.distanceM) + " " + (distanceUnit.equals("ft") ? "ft" : "m"));
            rssi.setText(net.level + " dBm");
            itemView.setOnClickListener(v -> l.onNetworkClick(net));
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_network, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.bind(data.get(position), listener, distanceUnit);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setDistanceUnit(String unit) {
        this.distanceUnit = unit;
        notifyDataSetChanged();
    }
}
