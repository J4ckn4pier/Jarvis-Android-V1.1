package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android reasoning must combine durable memory with current time/relevant normalized device state. */
public final class AndroidRuntimeEnvironmentContextContractTest {
    public static void main(String[] args) throws Exception {
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));

        check(runtime.contains("new RuntimeEnvironmentContextSource(clock, devices)"),
                "Android runtime must attach provider-neutral live environment context");
        check(runtime.contains("new CompositeAssistantContextSource"),
                "Android runtime must compose memory and live context rather than replacing either source");
        check(runtime.indexOf("DeviceStateStore devices") < runtime.indexOf("new AssistantCore"),
                "device context store must exist before reasoning context is composed");

        System.out.println("AndroidRuntimeEnvironmentContextContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
