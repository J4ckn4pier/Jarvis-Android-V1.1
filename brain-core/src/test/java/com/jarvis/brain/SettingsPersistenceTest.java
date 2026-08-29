package com.jarvis.brain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Proves non-secret settings persist while privacy-sensitive consent remains fail-closed. */
public final class SettingsPersistenceTest {
    public static void main(String[] args) {
        MapSettingsPersistence durable = new MapSettingsPersistence();
        SettingsStore first = new SettingsStore(durable);
        check(!first.bool("presence_followup_opt_in"), "presence consent must default false");
        first.put("presence_followup_opt_in", "true");
        first.put("operating_mode", "quiet");

        SettingsStore afterRestart = new SettingsStore(durable);
        check(afterRestart.bool("presence_followup_opt_in"), "explicit presence consent must survive restart");
        check("quiet".equals(afterRestart.get("operating_mode")), "ordinary non-secret setting must survive restart");
        check("Hey JARVIS".equals(afterRestart.get("wake_word")), "defaults must remain when not overridden");

        SettingsPersistence hostile = new SettingsPersistence() {
            @Override public Map<String,String> load() {
                Map<String,String> values = new LinkedHashMap<>();
                values.put("presence_followup_opt_in", "definitely");
                values.put("wake_word", "Computer");
                return values;
            }
            @Override public void put(String key, String value) {}
        };
        SettingsStore malformed = new SettingsStore(hostile);
        check(!malformed.bool("presence_followup_opt_in"),
                "malformed persisted privacy consent must fail closed rather than opt in");
        check("Computer".equals(malformed.get("wake_word")),
                "malformed privacy value must not discard unrelated persisted settings");

        SettingsPersistence broken = new SettingsPersistence() {
            @Override public Map<String,String> load() { throw new IllegalStateException("disk unavailable"); }
            @Override public void put(String key, String value) { throw new IllegalStateException("disk unavailable"); }
        };
        SettingsStore unavailable = new SettingsStore(broken);
        check(!unavailable.bool("presence_followup_opt_in"),
                "persistence load failure must leave privacy-sensitive consent disabled");
        unavailable.put("wake_word", "Jarvis");
        check("Jarvis".equals(unavailable.get("wake_word")),
                "persistence write failure must not break in-process settings state");

        System.out.println("SettingsPersistenceTest passed");
    }

    private static final class MapSettingsPersistence implements SettingsPersistence {
        private final Map<String,String> values = new LinkedHashMap<>();
        @Override public Map<String,String> load() { return Map.copyOf(values); }
        @Override public void put(String key, String value) { values.put(key, value); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
