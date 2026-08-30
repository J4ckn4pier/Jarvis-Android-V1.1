package com.jarvis.brain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Proves frontend default-app choices use the injected durable runtime store. */
public final class JarvisUiDefaultAppCompositionTest {
    public static void main(String[] args) {
        MapPersistence persistence = new MapPersistence();
        DefaultAppPreferenceStore shared = new DefaultAppPreferenceStore(persistence);
        JarvisUiBackend backend = new JarvisUiBackend(
                new LongTermMemoryStore(), ToolRegistry.standard(), new ConnectionRegistry(),
                new SettingsStore(), shared);

        check(backend.defaultApps() == shared,
                "UI backend must expose the injected runtime default-app store, not a shadow copy");
        backend.defaultApps().set("navigation", "com.example.maps");
        check(shared.get("navigation").orElseThrow().equals("com.example.maps"),
                "frontend default choice must immediately affect runtime source of truth");
        check(new DefaultAppPreferenceStore(persistence).get("navigation").isPresent(),
                "frontend default choice must survive restart through injected persistence");

        JarvisUiBackend compatibility = new JarvisUiBackend(null, null, null, new SettingsStore());
        check(compatibility.defaultApps() != null,
                "existing four-argument UI backend constructor must remain valid");

        System.out.println("JarvisUiDefaultAppCompositionTest passed");
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
