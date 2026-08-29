package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Debug assistant command fixtures must survive nested adb/device shell transport losslessly. */
public final class AndroidAssistantDebugCommandTransportContractTest {
    public static void main(String[] args) throws Exception {
        String receiver = Files.readString(Path.of("../android/app/src/debug/java/com/jarvis/mobile/assistant/JarvisAssistantTestReceiver.java"));
        String smoke = Files.readString(Path.of("../.github/scripts/overlay-decision-smoke.sh"));

        check(receiver.contains("jarvis_test_command_b64"),
                "debug receiver must expose a shell-safe encoded command extra");
        check(receiver.contains("Base64.decode"),
                "debug receiver must decode the encoded command on-device rather than trusting nested shell quoting");
        check(receiver.contains("StandardCharsets.UTF_8"),
                "debug receiver must decode command bytes deterministically as UTF-8");
        check(smoke.contains("JARVIS_TEST_COMMAND_B64="),
                "overlay device smoke must encode its fixture before crossing adb shell");
        check(smoke.contains("--es jarvis_test_command_b64"),
                "overlay device smoke must transport only the encoded shell-safe value");
        check(!smoke.contains("--es jarvis_test_command 'Jarvis, text Mom I am on my way'"),
                "overlay device smoke must not rely on nested shell quoting for multi-word fixtures");

        System.out.println("AndroidAssistantDebugCommandTransportContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
