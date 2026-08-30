package com.jarvis.brain;

import java.util.Map;

/** Persistence boundary for normalized vendor-neutral device state. */
public interface DeviceStateStorePersistence {
    Map<String,DeviceState> load();
    void put(DeviceState state);
    void remove(String id);

    static DeviceStateStorePersistence none() {
        return new DeviceStateStorePersistence() {
            @Override public Map<String,DeviceState> load() { return Map.of(); }
            @Override public void put(DeviceState state) { }
            @Override public void remove(String id) { }
        };
    }
}
