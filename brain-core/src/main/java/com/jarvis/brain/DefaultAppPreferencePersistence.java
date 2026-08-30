package com.jarvis.brain;

import java.util.Map;

/** Persistence boundary for user-selected default apps/services. */
public interface DefaultAppPreferencePersistence {
    Map<String,String> load();
    void put(String category, String appId);
    void remove(String category);

    static DefaultAppPreferencePersistence none() {
        return new DefaultAppPreferencePersistence() {
            @Override public Map<String,String> load() { return Map.of(); }
            @Override public void put(String category, String appId) {}
            @Override public void remove(String category) {}
        };
    }
}
