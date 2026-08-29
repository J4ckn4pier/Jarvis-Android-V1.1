package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins Android default-app/service choices to one app-private durable store shared with UI. */
public final class AndroidDefaultAppPreferencePersistenceContractTest {
    public static void main(String[] args) throws Exception {
        Path adapterPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidDefaultAppPreferencePersistence.java");
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        check(Files.exists(adapterPath), "Android must persist user-selected default apps/services");
        String adapter = Files.readString(adapterPath);
        String runtime = Files.readString(runtimePath);

        check(adapter.contains("implements DefaultAppPreferencePersistence"),
                "Android adapter must implement the shared default-app persistence port");
        check(adapter.contains("MODE_PRIVATE"), "default-app preferences must use app-private storage");
        check(adapter.contains("jarvis_default_apps"), "default-app preferences need a dedicated namespace");
        check(adapter.contains("remove(category)"), "manual default-app removal must be persisted");
        check(runtime.contains("new AndroidDefaultAppPreferencePersistence(app)"),
                "Android runtime must bind durable default-app persistence");
        check(runtime.contains("new DefaultAppPreferenceStore("),
                "Android runtime must own the durable default-app store");
        check(runtime.contains("new JarvisUiBackend(null, tools, connections, settings, defaultApps, lists, routines)"),
                "frontend facade must receive the same durable default-app store alongside durable lists and routines");

        System.out.println("AndroidDefaultAppPreferencePersistenceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
