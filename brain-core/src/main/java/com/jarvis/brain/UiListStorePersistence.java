package com.jarvis.brain;

import java.util.Map;

/** Persistence boundary for user-editable UI list state. */
public interface UiListStorePersistence {
    Map<UiSection,Map<String,UiListItem>> load();
    void put(UiSection section, UiListItem item);
    void remove(UiSection section, String id);

    static UiListStorePersistence none() {
        return new UiListStorePersistence() {
            @Override public Map<UiSection,Map<String,UiListItem>> load() { return Map.of(); }
            @Override public void put(UiSection section, UiListItem item) { }
            @Override public void remove(UiSection section, String id) { }
        };
    }
}
