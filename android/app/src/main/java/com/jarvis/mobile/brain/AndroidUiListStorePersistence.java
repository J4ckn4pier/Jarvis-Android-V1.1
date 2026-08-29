package com.jarvis.mobile.brain;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.jarvis.brain.UiListItem;
import com.jarvis.brain.UiListStorePersistence;
import com.jarvis.brain.UiSection;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/** App-private persistence for editable UI list state. Contains no credential material. */
public final class AndroidUiListStorePersistence implements UiListStorePersistence {
    private static final String NAME = "jarvis_ui_lists";
    private static final String SEPARATOR = ":";
    private final SharedPreferences preferences;

    public AndroidUiListStorePersistence(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        preferences = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    @Override
    public Map<UiSection,Map<String,UiListItem>> load() {
        Map<UiSection,Map<String,UiListItem>> restored = new EnumMap<>(UiSection.class);
        for (Map.Entry<String,?> entry : preferences.getAll().entrySet()) {
            if (!(entry.getValue() instanceof String encoded)) continue;
            UiSection section = sectionFromKey(entry.getKey());
            if (section == null) continue;
            UiListItem item = decode(encoded);
            if (item == null) continue;
            restored.computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(item.id(), item);
        }
        Map<UiSection,Map<String,UiListItem>> snapshot = new EnumMap<>(UiSection.class);
        for (Map.Entry<UiSection,Map<String,UiListItem>> entry : restored.entrySet()) {
            snapshot.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return snapshot;
    }

    @Override
    public void put(UiSection section, UiListItem item) {
        if (section == null || item == null) throw new IllegalArgumentException("section and item required");
        preferences.edit().putString(key(section, item.id()), encode(item)).apply();
    }

    @Override
    public void remove(UiSection section, String id) {
        if (section == null) throw new IllegalArgumentException("section required");
        preferences.edit().remove(key(section, id)).apply();
    }

    private static String key(UiSection section, String id) {
        String clean = id == null ? "" : id.trim();
        if (clean.isBlank()) throw new IllegalArgumentException("id required");
        String encodedId = Base64.encodeToString(
                clean.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return section.name() + SEPARATOR + encodedId;
    }

    private static UiSection sectionFromKey(String key) {
        if (key == null) return null;
        int split = key.indexOf(SEPARATOR);
        if (split <= 0) return null;
        try {
            return UiSection.valueOf(key.substring(0, split));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String encode(UiListItem item) {
        try {
            JSONObject attributes = new JSONObject();
            for (Map.Entry<String,String> entry : item.attributes().entrySet()) {
                attributes.put(entry.getKey(), entry.getValue());
            }
            JSONObject value = new JSONObject();
            value.put("id", item.id());
            value.put("title", item.title());
            value.put("details", item.details());
            value.put("completed", item.completed());
            value.put("attributes", attributes);
            return value.toString();
        } catch (JSONException failure) {
            throw new IllegalStateException("Unable to serialize UI list item", failure);
        }
    }

    private static UiListItem decode(String encoded) {
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
            return new UiListItem(
                    value.getString("id"),
                    value.getString("title"),
                    value.optString("details", ""),
                    value.optBoolean("completed", false),
                    attributes);
        } catch (JSONException | IllegalArgumentException ignored) {
            return null;
        }
    }
}
