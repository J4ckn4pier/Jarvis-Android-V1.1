package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins editable UI list state to one app-private durable store shared with the frontend facade. */
public final class AndroidUiListPersistenceContractTest {
    public static void main(String[] args) throws Exception {
        Path adapterPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidUiListStorePersistence.java");
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        check(Files.exists(adapterPath), "Android must persist editable UI list state");
        String adapter = Files.readString(adapterPath);
        String runtime = Files.readString(runtimePath);

        check(adapter.contains("implements UiListStorePersistence"),
                "Android list adapter must implement the shared persistence port");
        check(adapter.contains("MODE_PRIVATE"), "UI list state must use app-private storage");
        check(adapter.contains("jarvis_ui_lists"), "UI lists need a dedicated persistence namespace");
        check(adapter.contains("completed"), "serialized UI list state must preserve completion");
        check(adapter.contains("attributes"), "serialized UI list state must preserve provenance/attributes");
        check(runtime.contains("new AndroidUiListStorePersistence(app)"),
                "Android runtime must bind durable UI list persistence");
        check(runtime.contains("new UiListStore("),
                "Android runtime must own the durable UI list store");
        check(runtime.contains("new JarvisUiBackend(null, tools, connections, settings, defaultApps, lists)"),
                "frontend facade must receive the same durable UI list store");

        System.out.println("AndroidUiListPersistenceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
