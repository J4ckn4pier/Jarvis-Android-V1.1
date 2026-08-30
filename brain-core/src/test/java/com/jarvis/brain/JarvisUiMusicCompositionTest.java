package com.jarvis.brain;

/** Proves the frontend facade can share the runtime-owned durable music queue store. */
public final class JarvisUiMusicCompositionTest {
    public static void main(String[] args) {
        MusicQueueStore music = new MusicQueueStore();
        JarvisUiBackend backend = new JarvisUiBackend(
                null,
                ToolRegistry.standard(),
                new ConnectionRegistry(),
                new SettingsStore(),
                new DefaultAppPreferenceStore(),
                new UiListStore(),
                new RoutineStore(),
                new ActivityLog(),
                new DeviceStateStore(),
                music);
        check(backend.music() == music,
                "frontend facade must expose the same runtime-owned music queue store");
        System.out.println("JarvisUiMusicCompositionTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
