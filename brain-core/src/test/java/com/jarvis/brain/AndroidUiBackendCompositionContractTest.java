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
        check(runtime.contains("new JarvisUiBackend(null, tools, connections, settings, defaultApps, lists)"),
                "UI backend must share runtime tools, connections, persistent settings, durable default apps, and durable lists");
        check(runtime.contains("public JarvisUiBackend uiBackend()"),
                "Android frontend adapter must be able to reach the shared UI backend facade");

        System.out.println("AndroidUiBackendCompositionContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
