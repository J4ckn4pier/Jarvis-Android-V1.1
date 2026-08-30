package com.jarvis.brain;

/** Proves the frontend facade can share the runtime-owned durable activity/audit log. */
public final class JarvisUiActivityCompositionTest {
    public static void main(String[] args) {
        ActivityLog activity = new ActivityLog();
        JarvisUiBackend backend = new JarvisUiBackend(
                null,
                ToolRegistry.standard(),
                new ConnectionRegistry(),
                new SettingsStore(),
                new DefaultAppPreferenceStore(),
                new UiListStore(),
                new RoutineStore(),
                activity);
        check(backend.activity() == activity,
                "frontend facade must expose the same runtime-owned activity log");
        System.out.println("JarvisUiActivityCompositionTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
