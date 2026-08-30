package com.jarvis.brain;

import java.util.*;

/** Remembers per-category default apps/services chosen by the user. */
public final class DefaultAppPreferenceStore {
    private final Map<String,String> byCategory = new LinkedHashMap<>();
    private final DefaultAppPreferencePersistence persistence;

    public DefaultAppPreferenceStore() {
        this(DefaultAppPreferencePersistence.none());
    }

    public DefaultAppPreferenceStore(DefaultAppPreferencePersistence persistence) {
        this.persistence = persistence == null ? DefaultAppPreferencePersistence.none() : persistence;
        try {
            Map<String,String> restored = this.persistence.load();
            if (restored != null) {
                for (Map.Entry<String,String> entry : restored.entrySet()) {
                    String category = normalize(entry.getKey());
                    String appId = clean(entry.getValue());
                    if (!category.isBlank() && !appId.isBlank()) byCategory.put(category, appId);
                }
            }
        } catch (RuntimeException ignored) {
            // Availability of persistence must not prevent the assistant from using in-process choices.
        }
    }

    public synchronized void set(String category, String appId) {
        String c=normalize(category), a=clean(appId);
        if(c.isBlank()||a.isBlank()) throw new IllegalArgumentException("category and app required");
        byCategory.put(c, a);
        try { persistence.put(c, a); } catch (RuntimeException ignored) { }
    }

    public synchronized Optional<String> get(String category){
        return Optional.ofNullable(byCategory.get(normalize(category)));
    }

    public synchronized void remove(String category){
        String c = normalize(category);
        byCategory.remove(c);
        try { persistence.remove(c); } catch (RuntimeException ignored) { }
    }

    public synchronized Map<String,String> snapshot(){ return Map.copyOf(byCategory); }
    private static String normalize(String v){ return clean(v).toLowerCase(Locale.ROOT); }
    private static String clean(String v){return v==null?"":v.trim();}
}
