package com.jarvis.brain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Proves UI settings and runtime policy can share one persisted source of truth. */
public final class JarvisUiSettingsCompositionTest {
    public static void main(String[] args) {
        MapPersistence persistence = new MapPersistence();
        SettingsStore shared = new SettingsStore(persistence);
        JarvisUiBackend backend = new JarvisUiBackend(
                new LongTermMemoryStore(), ToolRegistry.standard(), new ConnectionRegistry(), shared);

        check(backend.settings() == shared,
                "UI backend must expose the injected runtime SettingsStore, not create a shadow copy");
        backend.settings().put(SettingsStore.PRESENCE_FOLLOWUP_OPT_IN, "true");
        check(shared.bool(SettingsStore.PRESENCE_FOLLOWUP_OPT_IN),
                "UI setting mutation must immediately affect runtime policy state");
        SettingsStore afterRestart = new SettingsStore(persistence);
        check(afterRestart.bool(SettingsStore.PRESENCE_FOLLOWUP_OPT_IN),
                "UI setting mutation must flow through the shared persistence adapter");

        JarvisUiBackend compatibility = new JarvisUiBackend(null, null, null);
        check(compatibility.settings() != null,
                "existing UI backend constructor must remain valid for tests/default composition");

        System.out.println("JarvisUiSettingsCompositionTest passed");
    }

    private static final class MapPersistence implements SettingsPersistence {
        private final Map<String,String> values = new LinkedHashMap<>();
        @Override public Map<String,String> load() { return Map.copyOf(values); }
        @Override public void put(String key, String value) { values.put(key, value); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
