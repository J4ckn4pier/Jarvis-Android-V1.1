package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Guards Android emulator command injection from adding literal shell quotes to user utterances. */
public final class EmulatorCommandInjectionContractTest {
    public static void main(String[] args) throws Exception {
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));
        check(smoke.contains("--es jarvis_test_command \"help me!!!\""),
                "help smoke command must be injected without literal apostrophes");
        check(smoke.contains("--es jarvis_test_command \"how are you\""),
                "shared-brain smoke command must be injected without literal apostrophes");
        check(!smoke.contains("\"'help me!!!'\""),
                "help smoke command must not contain literal shell quotes");
        check(!smoke.contains("\"'how are you'\""),
                "shared-brain smoke command must not contain literal shell quotes");
        System.out.println("EmulatorCommandInjectionContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
