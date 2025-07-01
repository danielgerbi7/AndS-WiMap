package com.example.wi_map.interfaces;


import android.net.wifi.ScanResult;

import com.example.wi_map.models.WifiEntry;

public interface INetworkClickListener {
    void onNetworkClick(WifiEntry network);
}