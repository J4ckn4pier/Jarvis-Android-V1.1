package com.jarvis.brain;

import java.util.Map;

/** Persistence boundary for the user-visible activity/audit timeline. */
public interface ActivityLogPersistence {
    Map<String,ActivityRecord> load();
    void put(ActivityRecord record);
    void remove(String id);

    static ActivityLogPersistence none() {
        return new ActivityLogPersistence() {
            @Override public Map<String,ActivityRecord> load() { return Map.of(); }
            @Override public void put(ActivityRecord record) { }
            @Override public void remove(String id) { }
        };
    }
}
