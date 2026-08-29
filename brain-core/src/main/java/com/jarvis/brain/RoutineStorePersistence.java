package com.jarvis.brain;

import java.util.Map;

/** Persistence boundary for user-created routine definitions. */
public interface RoutineStorePersistence {
    Map<String,RoutineDefinition> load();
    void put(RoutineDefinition routine);
    void remove(String id);

    static RoutineStorePersistence none() {
        return new RoutineStorePersistence() {
            @Override public Map<String,RoutineDefinition> load() { return Map.of(); }
            @Override public void put(RoutineDefinition routine) { }
            @Override public void remove(String id) { }
        };
    }
}
