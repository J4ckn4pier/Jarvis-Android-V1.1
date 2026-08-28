package com.jarvis.brain;

import java.util.*;

/** Remembers per-category default apps/services chosen by the user. */
public final class DefaultAppPreferenceStore {
    private final Map<String,String> byCategory = new LinkedHashMap<>();
    public synchronized void set(String category, String appId) {
        String c=clean(category), a=clean(appId);
        if(c.isBlank()||a.isBlank()) throw new IllegalArgumentException("category and app required");
        byCategory.put(c.toLowerCase(Locale.ROOT), a);
    }
    public synchronized Optional<String> get(String category){ return Optional.ofNullable(byCategory.get(clean(category).toLowerCase(Locale.ROOT))); }
    public synchronized void remove(String category){ byCategory.remove(clean(category).toLowerCase(Locale.ROOT)); }
    public synchronized Map<String,String> snapshot(){ return Map.copyOf(byCategory); }
    private static String clean(String v){return v==null?"":v.trim();}
}
