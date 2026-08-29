package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins UI-facing device state to app-private durable storage shared with the Android runtime facade. */
public final class AndroidDeviceStatePersistenceContractTest {
    public static void main(String[] args) throws Exception {
        Path adapterPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidDeviceStateStorePersistence.java");
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        check(Files.exists(adapterPath), "Android must persist normalized device state");
        String adapter = Files.readString(adapterPath);
        String runtime = Files.readString(runtimePath);

        check(adapter.contains("implements DeviceStateStorePersistence"),
                "Android device adapter must implement the shared persistence port");
        check(adapter.contains("MODE_PRIVATE"), "device state must use app-private storage");
        check(adapter.contains("jarvis_device_state"), "device state needs a dedicated namespace");
        check(adapter.contains("name"), "device serialization must preserve display name");
        check(adapter.contains("type"), "device serialization must preserve normalized type");
        check(adapter.contains("attributes"), "device serialization must preserve attributes");
        check(runtime.contains("new AndroidDeviceStateStorePersistence(app)"),
                "Android runtime must bind durable device persistence");
        check(runtime.contains("DeviceStateStore devices = new DeviceStateStore("),
                "Android runtime must own the durable device store");
        check(runtime.contains("new JarvisUiBackend(null, tools, connections, settings, defaultApps, lists, routines, activity, devices)"),
                "frontend facade must receive the same durable device store");

        System.out.println("AndroidDeviceStatePersistenceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
