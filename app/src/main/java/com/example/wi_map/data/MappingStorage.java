package com.example.wi_map.data;

import android.content.Context;

import com.example.wi_map.models.HistoryEntry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MappingStorage {
    private static final String PREFS = "wi_map_prefs";
    private static final String KEY_HISTORY = "history_list";

    private final android.content.SharedPreferences prefs;
    private final Gson gson = new Gson();

    public MappingStorage(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<HistoryEntry> loadAll() {
        String json = prefs.getString(KEY_HISTORY, null);
        if (json == null) return new ArrayList<>();
        Type listType = new TypeToken<List<HistoryEntry>>(){}.getType();
        return gson.fromJson(json, listType);
    }

    public void saveAll(List<HistoryEntry> list) {
        String json = gson.toJson(list);
        prefs.edit().putString(KEY_HISTORY, json).apply();
    }

    public void addEntry(HistoryEntry e) {
        List<HistoryEntry> list = loadAll();
        list.add(0, e);
        saveAll(list);
    }

    public void clearAll() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }
}
