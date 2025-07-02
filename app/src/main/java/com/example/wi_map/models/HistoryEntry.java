package com.example.wi_map.models;

public class HistoryEntry {
    public long timestampMs;
    public String ssid;
    public int levelDbm;
    public float distance;
    public String distanceUnit;
    public double lat;
    public double lng;

    public HistoryEntry(long timestampMs,
                        String ssid,
                        int levelDbm,
                        float distance,
                        String distanceUnit,
                        double lat,
                        double lng) {
        this.timestampMs   = timestampMs;
        this.ssid          = ssid;
        this.levelDbm      = levelDbm;
        this.distance      = distance;
        this.distanceUnit  = distanceUnit;
        this.lat           = lat;
        this.lng           = lng;
    }
}

