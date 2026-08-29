package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins the user-visible activity/audit timeline to app-private durable storage shared with UI. */
public final class AndroidActivityLogPersistenceContractTest {
    public static void main(String[] args) throws Exception {
        Path adapterPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidActivityLogPersistence.java");
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        check(Files.exists(adapterPath), "Android must persist the activity/audit timeline");
        String adapter = Files.readString(adapterPath);
        String runtime = Files.readString(runtimePath);

        check(adapter.contains("implements ActivityLogPersistence"),
                "Android activity adapter must implement the shared persistence port");
        check(adapter.contains("MODE_PRIVATE"), "activity audit state must use app-private storage");
        check(adapter.contains("jarvis_activity_log"), "activity audit state needs a dedicated namespace");
        check(adapter.contains("status"), "activity serialization must preserve unresolved/failure status");
        check(adapter.contains("evidence"), "activity serialization must preserve evidence/provenance");
        check(adapter.contains("at"), "activity serialization must preserve timestamp");
        check(runtime.contains("new AndroidActivityLogPersistence(app)"),
                "Android runtime must bind durable activity persistence");
        check(runtime.contains("ActivityLog activity = new ActivityLog("),
                "Android runtime must own the durable activity log");
        check(runtime.contains("new JarvisUiBackend(null, tools, connections, settings, defaultApps, lists, routines, activity, devices)"),
                "frontend facade must receive the same durable activity log in the full shared composition");

        System.out.println("AndroidActivityLogPersistenceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
