package com.jarvis.mobile.brain;

import android.content.Context;
import android.content.SharedPreferences;
import com.jarvis.brain.DefaultAppPreferencePersistence;
import java.util.LinkedHashMap;
import java.util.Map;

/** App-private persistence for user-selected default apps/services. */
public final class AndroidDefaultAppPreferencePersistence implements DefaultAppPreferencePersistence {
    private static final String NAME = "jarvis_default_apps";
    private final SharedPreferences preferences;

    public AndroidDefaultAppPreferencePersistence(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        preferences = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    @Override
    public Map<String,String> load() {
        Map<String,String> values = new LinkedHashMap<>();
        for (Map.Entry<String,?> entry : preferences.getAll().entrySet()) {
            if (entry.getValue() instanceof String value) values.put(entry.getKey(), value);
        }
        return Map.copyOf(values);
    }

    @Override
    public void put(String category, String appId) {
        preferences.edit().putString(category, appId).apply();
    }

    @Override
    public void remove(String category) {
        preferences.edit().remove(category).apply();
    }
}
