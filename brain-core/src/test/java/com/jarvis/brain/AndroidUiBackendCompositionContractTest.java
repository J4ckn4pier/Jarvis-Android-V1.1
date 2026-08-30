package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins Android to one backend facade sharing runtime-owned durable stores with UI surfaces. */
public final class AndroidUiBackendCompositionContractTest {
    public static void main(String[] args) throws Exception {
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        String runtime = Files.readString(runtimePath);

        check(runtime.contains("private final JarvisUiBackend uiBackend"),
                "Android runtime must own the backend facade used by frontend surfaces");
        check(runtime.contains("ConnectionRegistry connections ="),
                "Android runtime must own one connection registry for backend/UI composition");
        check(runtime.contains("UiListStore lists ="),
                "Android runtime must own one editable-list store for backend/UI composition");
        check(runtime.contains("RoutineStore routines ="),
                "Android runtime must own one routine store for backend/UI composition");
        check(runtime.contains("ActivityLog activity ="),
                "Android runtime must own one activity/audit store for backend/UI composition");
        check(runtime.contains("DeviceStateStore devices ="),
                "Android runtime must own one device-state store for backend/UI composition");
        check(runtime.contains("MusicQueueStore music ="),
                "Android runtime must own one music queue store for backend/UI composition");
        check(runtime.contains("settings, defaultApps, lists, routines, activity, devices, music)"),
                "UI backend must share settings, default apps, lists, routines, activity, devices, and music");
        check(runtime.contains("public JarvisUiBackend uiBackend()"),
                "Android frontend adapter must be able to reach the shared UI backend facade");

        System.out.println("AndroidUiBackendCompositionContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
