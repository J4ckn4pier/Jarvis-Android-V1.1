package com.jarvis.mobile.brain;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.jarvis.brain.ActivityLogPersistence;
import com.jarvis.brain.ActivityRecord;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/** App-private persistence for the user-visible activity/audit timeline. Contains no credentials. */
public final class AndroidActivityLogPersistence implements ActivityLogPersistence {
    private static final String NAME = "jarvis_activity_log";
    private final SharedPreferences preferences;

    public AndroidActivityLogPersistence(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        preferences = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    @Override
    public Map<String,ActivityRecord> load() {
        Map<String,ActivityRecord> restored = new LinkedHashMap<>();
        for (Map.Entry<String,?> entry : preferences.getAll().entrySet()) {
            if (!(entry.getValue() instanceof String encoded)) continue;
            ActivityRecord record = decode(encoded);
            if (record != null) restored.put(record.id(), record);
        }
        return Map.copyOf(restored);
    }

    @Override
    public void put(ActivityRecord record) {
        if (record == null) throw new IllegalArgumentException("record required");
        preferences.edit().putString(key(record.id()), encode(record)).apply();
    }

    @Override
    public void remove(String id) {
        preferences.edit().remove(key(id)).apply();
    }

    private static String key(String id) {
        String clean = id == null ? "" : id.trim();
        if (clean.isBlank()) throw new IllegalArgumentException("id required");
        return Base64.encodeToString(
                clean.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String encode(ActivityRecord record) {
        try {
            JSONObject evidence = new JSONObject();
            for (Map.Entry<String,String> entry : record.evidence().entrySet()) {
                evidence.put(entry.getKey(), entry.getValue());
            }
            JSONObject value = new JSONObject();
            value.put("id", record.id());
            value.put("at", record.at().toString());
            value.put("title", record.title());
            value.put("status", record.status().name());
            value.put("detail", record.detail());
            value.put("evidence", evidence);
            return value.toString();
        } catch (JSONException failure) {
            throw new IllegalStateException("Unable to serialize activity record", failure);
        }
    }

    private static ActivityRecord decode(String encoded) {
        try {
            JSONObject value = new JSONObject(encoded);
            JSONObject evidenceJson = value.optJSONObject("evidence");
            Map<String,String> evidence = new LinkedHashMap<>();
            if (evidenceJson != null) {
                Iterator<String> keys = evidenceJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String evidenceValue = evidenceJson.optString(key, null);
                    if (evidenceValue != null) evidence.put(key, evidenceValue);
                }
            }
            return new ActivityRecord(
                    value.getString("id"),
                    Instant.parse(value.getString("at")),
                    value.getString("title"),
                    ActivityRecord.Status.valueOf(value.getString("status")),
                    value.optString("detail", ""),
                    evidence);
        } catch (JSONException | IllegalArgumentException ignored) {
            return null;
        }
    }
}
