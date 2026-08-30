package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Guards Android emulator command injection across both the host shell and adb's remote shell. */
public final class EmulatorCommandInjectionContractTest {
    public static void main(String[] args) throws Exception {
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));
        check(smoke.contains("--es jarvis_test_command '\"help me!!!\"'"),
                "help smoke command must transmit remote-shell grouping quotes");
        check(smoke.contains("--es jarvis_test_command '\"how are you\"'"),
                "shared-brain smoke command must transmit remote-shell grouping quotes");
        check(!smoke.contains("--es jarvis_test_command \"help me!!!\""),
                "host-only quoting is stripped before adb remote shell and must not be used");
        check(!smoke.contains("--es jarvis_test_command \"how are you\""),
                "shared-brain command must not rely on host-only quoting");
        System.out.println("EmulatorCommandInjectionContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
