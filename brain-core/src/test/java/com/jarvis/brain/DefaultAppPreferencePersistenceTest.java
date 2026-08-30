package com.jarvis.brain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Proves user-chosen default apps/services survive restart and removals persist. */
public final class DefaultAppPreferencePersistenceTest {
    public static void main(String[] args) {
        MapPersistence persistence = new MapPersistence();
        DefaultAppPreferenceStore first = new DefaultAppPreferenceStore(persistence);
        first.set("navigation", "com.google.android.apps.maps");
        first.set("music", "com.spotify.music");

        DefaultAppPreferenceStore afterRestart = new DefaultAppPreferenceStore(persistence);
        check(afterRestart.get("NAVIGATION").orElseThrow().equals("com.google.android.apps.maps"),
                "default app category lookup must remain case-insensitive after restart");
        check(afterRestart.get("music").orElseThrow().equals("com.spotify.music"),
                "user-selected service must survive restart");

        afterRestart.remove("music");
        DefaultAppPreferenceStore afterRemovalRestart = new DefaultAppPreferenceStore(persistence);
        check(afterRemovalRestart.get("music").isEmpty(),
                "manual removal must persist rather than resurrect after restart");
        check(afterRemovalRestart.snapshot().size() == 1,
                "removing one category must preserve unrelated defaults");

        DefaultAppPreferencePersistence broken = new DefaultAppPreferencePersistence() {
            @Override public Map<String,String> load() { throw new IllegalStateException("unavailable"); }
            @Override public void put(String category, String appId) { throw new IllegalStateException("unavailable"); }
            @Override public void remove(String category) { throw new IllegalStateException("unavailable"); }
        };
        DefaultAppPreferenceStore resilient = new DefaultAppPreferenceStore(broken);
        resilient.set("browser", "org.mozilla.firefox");
        check(resilient.get("browser").orElseThrow().equals("org.mozilla.firefox"),
                "persistence failure must not break in-process preference state");
        resilient.remove("browser");
        check(resilient.get("browser").isEmpty(),
                "persistence failure must not prevent in-process removal");

        System.out.println("DefaultAppPreferencePersistenceTest passed");
    }

    private static final class MapPersistence implements DefaultAppPreferencePersistence {
        private final Map<String,String> values = new LinkedHashMap<>();
        @Override public Map<String,String> load() { return Map.copyOf(values); }
        @Override public void put(String category, String appId) { values.put(category, appId); }
        @Override public void remove(String category) { values.remove(category); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
