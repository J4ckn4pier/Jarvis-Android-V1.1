package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins routines to app-private durable storage while preserving approval metadata. */
public final class AndroidRoutinePersistenceContractTest {
    public static void main(String[] args) throws Exception {
        Path adapterPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidRoutineStorePersistence.java");
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        check(Files.exists(adapterPath), "Android must persist user-created routines");
        String adapter = Files.readString(adapterPath);
        String runtime = Files.readString(runtimePath);

        check(adapter.contains("implements RoutineStorePersistence"),
                "Android routine adapter must implement the shared persistence port");
        check(adapter.contains("MODE_PRIVATE"), "routine state must use app-private storage");
        check(adapter.contains("jarvis_routines"), "routines need a dedicated persistence namespace");
        check(adapter.contains("consequential"),
                "routine serialization must preserve approval/consequential metadata");
        check(adapter.contains("triggerArguments"), "routine trigger arguments must be serialized");
        check(adapter.contains("actionPlan"), "routine action plans must be serialized");
        check(runtime.contains("new AndroidRoutineStorePersistence(app)"),
                "Android runtime must bind durable routine persistence");
        check(runtime.contains("RoutineStore routines = new RoutineStore("),
                "Android runtime must own the durable routine store");
        check(runtime.contains("new JarvisUiBackend(null, tools, connections, settings, defaultApps, lists, routines, activity, devices)"),
                "frontend facade must receive the same durable routine store in the full shared composition");

        System.out.println("AndroidRoutinePersistenceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
