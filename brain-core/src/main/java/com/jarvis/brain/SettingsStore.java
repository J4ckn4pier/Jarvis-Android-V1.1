package com.jarvis.brain;

import java.util.*;

/** Typed-enough key/value settings state backing the Settings screen. */
public final class SettingsStore {
    private final Map<String,String> values = new LinkedHashMap<>();

    public SettingsStore() {
        values.put("voice", "jarvis-local-default");
        values.put("wake_word", "Hey JARVIS");
        values.put("voice_model", "local");
        values.put("language", "en-US");
        values.put("personality", "humble-butler-beta");
        values.put("proactive_enabled", "true");
        values.put("offline_enabled", "true");
        values.put("overlay_enabled", "true");
        values.put("ambient_orb_enabled", "false");
    }

    public synchronized void put(String key, String value) {
        String k = clean(key), v = clean(value);
        if (k.isBlank()) throw new IllegalArgumentException("setting key required");
        values.put(k, v);
    }
    public synchronized String get(String key) { return values.getOrDefault(clean(key), ""); }
    public synchronized boolean bool(String key) { return Boolean.parseBoolean(get(key)); }
    public synchronized Map<String,String> snapshot() { return Map.copyOf(values); }
    public synchronized List<Map.Entry<String,String>> search(String query) {
        String q = clean(query).toLowerCase(Locale.ROOT);
        return values.entrySet().stream().filter(e -> q.isBlank() || (e.getKey()+" "+e.getValue()).toLowerCase(Locale.ROOT).contains(q)).toList();
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
