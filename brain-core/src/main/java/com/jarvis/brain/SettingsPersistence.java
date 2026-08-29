package com.jarvis.brain;

import java.util.Map;

/** Persistence boundary for non-secret JARVIS settings. Credentials must use platform secure storage instead. */
public interface SettingsPersistence {
    Map<String,String> load();
    void put(String key, String value);

    static SettingsPersistence none() {
        return new SettingsPersistence() {
            @Override public Map<String,String> load() { return Map.of(); }
            @Override public void put(String key, String value) {}
        };
    }
}
