package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Granting the Android Assistant role must immediately re-arm JARVIS passive wake. */
public final class AndroidAssistantRoleWakeRearmContractTest {
    public static void main(String[] args) throws Exception {
        String main = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));
        check(main.contains("requestCode == ASSISTANT_ROLE_REQUEST"), "MainActivity must handle Assistant role result");
        check(main.contains("JarvisVoiceInteractionService.refreshPassiveWakePreference()"),
                "Assistant role completion must explicitly refresh passive wake");
        System.out.println("AndroidAssistantRoleWakeRearmContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
