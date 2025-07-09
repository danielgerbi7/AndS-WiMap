package com.example.wi_map.models;

public class WifiEntry {
    public final String ssid;
    public final int level;
    public final float distanceM;
    public final String bssid;
    public final double latitude;
    public final double longitude;

    public WifiEntry(String ssid, int level, float distanceM, String bssid, double latitude, double longitude) {
        this.ssid = ssid;
        this.level = level;
        this.distanceM = distanceM;
        this.bssid = bssid;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
