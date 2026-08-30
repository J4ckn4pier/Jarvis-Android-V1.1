package com.jarvis.mobile.brain;

import android.content.Context;
import android.content.SharedPreferences;
import com.jarvis.brain.SettingsPersistence;
import java.util.LinkedHashMap;
import java.util.Map;

/** App-private persistence for non-secret brain settings only. Credentials use SecureSecretStore. */
public final class AndroidSharedPreferencesSettingsPersistence implements SettingsPersistence {
    private static final String NAME = "jarvis_brain_settings";
    private final SharedPreferences preferences;

    public AndroidSharedPreferencesSettingsPersistence(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        preferences = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    @Override
    public Map<String,String> load() {
        Map<String,String> values = new LinkedHashMap<>();
        for (Map.Entry<String,?> entry : preferences.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) values.put(entry.getKey(), text);
            else if (value instanceof Boolean flag) values.put(entry.getKey(), Boolean.toString(flag));
        }
        return Map.copyOf(values);
    }

    @Override
    public void put(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }
}
