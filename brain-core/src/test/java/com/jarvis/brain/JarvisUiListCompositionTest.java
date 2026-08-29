package com.jarvis.brain;

/** Proves the frontend facade can share the runtime-owned durable editable-list store. */
public final class JarvisUiListCompositionTest {
    public static void main(String[] args) {
        UiListStore lists = new UiListStore();
        JarvisUiBackend backend = new JarvisUiBackend(
                null,
                ToolRegistry.standard(),
                new ConnectionRegistry(),
                new SettingsStore(),
                new DefaultAppPreferenceStore(),
                lists);
        check(backend.lists() == lists,
                "frontend facade must expose the same runtime-owned editable-list store");
        System.out.println("JarvisUiListCompositionTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
