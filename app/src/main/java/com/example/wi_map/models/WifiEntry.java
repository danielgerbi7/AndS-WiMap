package com.example.wi_map.models;

public class WifiEntry {
    public final String ssid;
    public final int level;        // dBm
    public final float distanceM;  // meters
    public final String fingerprintLocation;

    public WifiEntry(String ssid, int level, float distanceM, String fingerprintLocation) {
        this.ssid = ssid;
        this.level = level;
        this.distanceM = distanceM;
        this.fingerprintLocation = fingerprintLocation;
    }
}
