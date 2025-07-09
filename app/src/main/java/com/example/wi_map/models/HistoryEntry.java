// com.example.wi_map.models.HistoryEntry
package com.example.wi_map.models;

public class HistoryEntry {
    public final long timestampMs;
    public final double latitude, longitude;
    public final String label;

    public HistoryEntry(long timestampMs, String label, double latitude, double longitude) {
        this.timestampMs = timestampMs;
        this.label = label;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
