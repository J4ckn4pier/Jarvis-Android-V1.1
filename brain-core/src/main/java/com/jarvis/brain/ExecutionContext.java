package com.jarvis.brain;

import java.util.HashMap;
import java.util.Map;

public final class ExecutionContext {
    private final Map<String, String> values = new HashMap<>();
    public void put(String key, String value) { values.put(key, value); }
    public String get(String key) { return values.get(key); }
    public Map<String, String> snapshot() { return Map.copyOf(values); }
}
