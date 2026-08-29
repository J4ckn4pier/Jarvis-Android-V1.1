package com.jarvis.brain;

import java.util.*;

/** Typed-enough non-secret settings state backing JARVIS surfaces. */
public final class SettingsStore {
    public static final String PRESENCE_FOLLOWUP_OPT_IN = "presence_followup_opt_in";
    private final Map<String,String> values = new LinkedHashMap<>();
    private final SettingsPersistence persistence;

    public SettingsStore() {
        this(SettingsPersistence.none());
    }

    public SettingsStore(SettingsPersistence persistence) {
        this.persistence = persistence == null ? SettingsPersistence.none() : persistence;
        loadDefaults();
        try {
            Map<String,String> restored = this.persistence.load();
            if (restored != null) {
                for (Map.Entry<String,String> entry : restored.entrySet()) {
                    String key = clean(entry.getKey());
                    String value = clean(entry.getValue());
                    if (key.isBlank()) continue;
                    if (PRESENCE_FOLLOWUP_OPT_IN.equals(key)) {
                        values.put(key, "true".equalsIgnoreCase(value) ? "true" : "false");
                    } else {
                        values.put(key, value);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            values.put(PRESENCE_FOLLOWUP_OPT_IN, "false");
        }
    }

    public synchronized void put(String key, String value) {
        String k = clean(key), v = clean(value);
        if (k.isBlank()) throw new IllegalArgumentException("setting key required");
        if (PRESENCE_FOLLOWUP_OPT_IN.equals(k)) {
            v = "true".equalsIgnoreCase(v) ? "true" : "false";
        }
        values.put(k, v);
        try { persistence.put(k, v); } catch (RuntimeException ignored) { /* in-process state remains usable */ }
    }

    public synchronized String get(String key) { return values.getOrDefault(clean(key), ""); }
    public synchronized boolean bool(String key) { return Boolean.parseBoolean(get(key)); }
    public synchronized Map<String,String> snapshot() { return Map.copyOf(values); }
    public synchronized List<Map.Entry<String,String>> search(String query) {
        String q = clean(query).toLowerCase(Locale.ROOT);
        return values.entrySet().stream().filter(e -> q.isBlank() || (e.getKey()+" "+e.getValue()).toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    private void loadDefaults() {
        values.put("voice", "jarvis-local-default");
        values.put("wake_word", "Hey JARVIS");
        values.put("voice_model", "local");
        values.put("language", "en-US");
        values.put("personality", "humble-butler-beta");
        values.put("proactive_enabled", "true");
        values.put(PRESENCE_FOLLOWUP_OPT_IN, "false");
        values.put("offline_enabled", "true");
        values.put("overlay_enabled", "true");
        values.put("ambient_orb_enabled", "false");
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
