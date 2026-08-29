package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Device-only command smoke must expose enough evidence to locate a lost command without weakening assertions. */
public final class AndroidCommandTraceContractTest {
    public static void main(String[] args) throws Exception {
        String activity = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));

        check(activity.contains("JARVIS_COMMAND_ENTRY"), "MainActivity must log command-test value at Activity entry");
        check(activity.contains("JARVIS_COMMAND_DISPATCH"), "MainActivity must log normalized value before runtime dispatch");
        check(runtime.contains("JARVIS_RUNTIME_INPUT"), "AndroidBrainRuntime must log presentation input");
        check(runtime.contains("JARVIS_RUNTIME_OUTPUT"), "AndroidBrainRuntime must log presentation output state/text");
        check(activity.contains("JARVIS_PRESENTATION_OUTPUT"), "MainActivity must log projected presentation state/text");
        check(smoke.contains("JARVIS_COMMAND_ENTRY|JARVIS_COMMAND_DISPATCH|JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_PRESENTATION_OUTPUT|JARVIS_COMMAND_RESULT"),
                "emulator failure output must collect the complete command trace");
        System.out.println("AndroidCommandTraceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
