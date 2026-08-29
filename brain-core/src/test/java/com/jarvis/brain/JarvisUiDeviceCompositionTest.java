package com.jarvis.brain;

/** Proves the frontend facade can share the runtime-owned durable device store. */
public final class JarvisUiDeviceCompositionTest {
    public static void main(String[] args) {
        DeviceStateStore devices = new DeviceStateStore();
        JarvisUiBackend backend = new JarvisUiBackend(
                null,
                ToolRegistry.standard(),
                new ConnectionRegistry(),
                new SettingsStore(),
                new DefaultAppPreferenceStore(),
                new UiListStore(),
                new RoutineStore(),
                new ActivityLog(),
                devices);
        check(backend.devices() == devices,
                "frontend facade must expose the same runtime-owned device state store");
        System.out.println("JarvisUiDeviceCompositionTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
