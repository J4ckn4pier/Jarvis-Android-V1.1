package com.jarvis.brain;

/** Proves the frontend facade can share the runtime-owned durable routine store. */
public final class JarvisUiRoutineCompositionTest {
    public static void main(String[] args) {
        RoutineStore routines = new RoutineStore();
        JarvisUiBackend backend = new JarvisUiBackend(
                null,
                ToolRegistry.standard(),
                new ConnectionRegistry(),
                new SettingsStore(),
                new DefaultAppPreferenceStore(),
                new UiListStore(),
                routines);
        check(backend.routines() == routines,
                "frontend facade must expose the same runtime-owned routine store");
        System.out.println("JarvisUiRoutineCompositionTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
