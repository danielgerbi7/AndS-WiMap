package com.example.wi_map.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wi_map.R;
import com.example.wi_map.models.HistoryEntry;
import com.google.android.material.textview.MaterialTextView;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class HistoryAdapter
        extends ListAdapter<HistoryEntry, HistoryAdapter.ViewHolder> {

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public HistoryAdapter() {
        super(DIFF_CALLBACK);
        timeFormat.setTimeZone(TimeZone.getDefault());
    }

    private static final DiffUtil.ItemCallback<HistoryEntry> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull HistoryEntry oldItem,
                                               @NonNull HistoryEntry newItem) {
                    return oldItem.timestampMs == newItem.timestampMs
                            && oldItem.ssid.equals(newItem.ssid);
                }

                @Override
                public boolean areContentsTheSame(@NonNull HistoryEntry oldItem,
                                                  @NonNull HistoryEntry newItem) {
                    return oldItem.timestampMs == newItem.timestampMs
                            && oldItem.ssid.equals(newItem.ssid)
                            && oldItem.levelDbm == newItem.levelDbm
                            && Float.compare(oldItem.distance, newItem.distance) == 0
                            && oldItem.distanceUnit.equals(newItem.distanceUnit);
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                         int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder,
                                 int position) {
        HistoryEntry entry = getItem(position);

        holder.tvSsid.setText(entry.ssid);

        holder.tvTime.setText(timeFormat.format(entry.timestampMs));

        holder.tvRssi.setText(entry.levelDbm + " dBm");

        String unit = entry.distanceUnit != null && entry.distanceUnit.equals("ft")
                ? "ft" : "m";
        holder.tvDistance.setText(Math.round(entry.distance) + " " + unit);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialTextView tvSsid;
        final MaterialTextView tvTime;
        final MaterialTextView tvRssi;
        final MaterialTextView tvDistance;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSsid = itemView.findViewById(R.id.tv_ssid);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvRssi = itemView.findViewById(R.id.tv_rssi);
            tvDistance = itemView.findViewById(R.id.tv_distance);
        }
    }
}
