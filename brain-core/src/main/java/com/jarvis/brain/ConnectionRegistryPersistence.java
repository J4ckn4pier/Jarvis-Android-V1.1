package com.jarvis.brain;

import java.util.Map;

/** Persistence boundary for non-secret connection/auth state. Credential material stays platform-secure. */
public interface ConnectionRegistryPersistence {
    Map<String,ConnectionState> load();
    void put(ConnectionState state);

    static ConnectionRegistryPersistence none() {
        return new ConnectionRegistryPersistence() {
            @Override public Map<String,ConnectionState> load() { return Map.of(); }
            @Override public void put(ConnectionState state) {}
        };
    }
}
