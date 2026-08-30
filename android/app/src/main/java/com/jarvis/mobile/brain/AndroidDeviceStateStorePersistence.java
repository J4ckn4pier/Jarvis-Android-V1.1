package com.jarvis.mobile.brain;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.jarvis.brain.DeviceState;
import com.jarvis.brain.DeviceStateStorePersistence;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/** App-private persistence for normalized vendor-neutral device state. Contains no credentials. */
public final class AndroidDeviceStateStorePersistence implements DeviceStateStorePersistence {
    private static final String NAME = "jarvis_device_state";
    private final SharedPreferences preferences;

    public AndroidDeviceStateStorePersistence(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        preferences = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    @Override
    public Map<String,DeviceState> load() {
        Map<String,DeviceState> restored = new LinkedHashMap<>();
        for (Map.Entry<String,?> entry : preferences.getAll().entrySet()) {
            if (!(entry.getValue() instanceof String encoded)) continue;
            DeviceState state = decode(encoded);
            if (state != null) restored.put(state.id(), state);
        }
        return Map.copyOf(restored);
    }

    @Override
    public void put(DeviceState state) {
        if (state == null) throw new IllegalArgumentException("device required");
        preferences.edit().putString(key(state.id()), encode(state)).apply();
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

    private static String encode(DeviceState state) {
        try {
            JSONObject attributes = new JSONObject();
            for (Map.Entry<String,String> entry : state.attributes().entrySet()) {
                attributes.put(entry.getKey(), entry.getValue());
            }
            JSONObject value = new JSONObject();
            value.put("id", state.id());
            value.put("name", state.name());
            value.put("type", state.type());
            value.put("on", state.on());
            value.put("attributes", attributes);
            return value.toString();
        } catch (JSONException failure) {
            throw new IllegalStateException("Unable to serialize device state", failure);
        }
    }

    private static DeviceState decode(String encoded) {
        try {
            JSONObject value = new JSONObject(encoded);
            JSONObject attributesJson = value.optJSONObject("attributes");
            Map<String,String> attributes = new LinkedHashMap<>();
            if (attributesJson != null) {
                Iterator<String> keys = attributesJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String attributeValue = attributesJson.optString(key, null);
                    if (attributeValue != null) attributes.put(key, attributeValue);
                }
            }
            return new DeviceState(
                    value.getString("id"),
                    value.getString("name"),
                    value.optString("type", "generic"),
                    value.optBoolean("on", false),
                    attributes);
        } catch (JSONException | IllegalArgumentException ignored) {
            return null;
        }
    }
}
