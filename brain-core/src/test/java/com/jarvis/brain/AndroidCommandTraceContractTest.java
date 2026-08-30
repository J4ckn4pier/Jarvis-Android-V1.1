package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Device-only command smoke must expose enough evidence to locate a lost command without weakening assertions. */
public final class AndroidCommandTraceContractTest {
    public static void main(String[] args) throws Exception {
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));

        check(runtime.contains("JARVIS_RUNTIME_INPUT"), "AndroidBrainRuntime must log presentation input");
        check(runtime.contains("JARVIS_RUNTIME_OUTPUT"), "AndroidBrainRuntime must log presentation output state/text");
        check(smoke.contains("emulator-command-launch.txt"), "emulator must preserve Activity launch evidence");
        check(smoke.contains("JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_COMMAND_RESULT"),
                "emulator failure output must collect shared-runtime and final-result trace evidence");
        check(smoke.contains("test \"$COMMAND_PASSED\" -eq 1"), "trace instrumentation must not weaken command acceptance");
        System.out.println("AndroidCommandTraceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
