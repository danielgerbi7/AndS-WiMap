package com.example.wi_map.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.wi_map.models.WifiEntry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class FingerprintStorage {
    private static final String PREFS_NAME = "wi_map_prefs";
    private static final String KEY_FINGERPRINTS = "fingerprints";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public FingerprintStorage(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Map<String, WifiEntry> loadAll() {
        String json = prefs.getString(KEY_FINGERPRINTS, null);
        if (json == null) {
            return new HashMap<>();
        }
        Type mapType = new TypeToken<Map<String, WifiEntry>>() {
        }.getType();
        return gson.fromJson(json, mapType);
    }

    public void saveAll(Map<String, WifiEntry> map) {
        String json = gson.toJson(map);
        prefs.edit().putString(KEY_FINGERPRINTS, json).apply();
    }


    public void save(WifiEntry entry) {
        Map<String, WifiEntry> map = loadAll();
        map.put(entry.bssid, entry);
        saveAll(map);
    }


    public void clearAll() {
        prefs.edit().remove(KEY_FINGERPRINTS).apply();
    }
}
