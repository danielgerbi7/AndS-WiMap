// com.example.wi_map.adapters.HistoryAdapter
package com.example.wi_map.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wi_map.R;
import com.example.wi_map.models.HistoryEntry;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class HistoryAdapter extends ListAdapter<HistoryEntry, HistoryAdapter.VH> {

    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public HistoryAdapter() {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull HistoryEntry a, @NonNull HistoryEntry b) {
                return a.timestampMs == b.timestampMs;
            }

            @Override
            public boolean areContentsTheSame(@NonNull HistoryEntry a, @NonNull HistoryEntry b) {
                return a.timestampMs == b.timestampMs && a.latitude == b.latitude && a.longitude == b.longitude && a.label.equals(b.label);
            }
        });
        timeFmt.setTimeZone(TimeZone.getDefault());
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(ViewGroup p, int viewType) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_history, p, false));
    }

    @Override
    public void onBindViewHolder(VH h, int pos) {
        HistoryEntry e = getItem(pos);
        h.tvLabel.setText(e.label);
        h.tvTime.setText(timeFmt.format(e.timestampMs));
        h.tvCoords.setText(String.format(Locale.getDefault(), "Lat: %.5f, Lon: %.5f", e.latitude, e.longitude));
    }

    public static class VH extends RecyclerView.ViewHolder {
        final TextView tvLabel, tvTime, tvCoords;

        VH(View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tv_label);
            tvTime = v.findViewById(R.id.tv_time);
            tvCoords = v.findViewById(R.id.tv_coords);
        }
    }
}
