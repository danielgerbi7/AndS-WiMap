package com.example.wi_map.interfaces;


import android.net.wifi.ScanResult;

public interface INetworkClickListener {
    void onNetworkClick(ScanResult network);
}