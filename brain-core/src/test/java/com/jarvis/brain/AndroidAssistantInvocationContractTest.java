package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pins the emulator assistant smoke to Android's VoiceInteractionManager service/session path.
 * Hardware key routing is OEM/device behavior and must not be the sole proof that our registered
 * VoiceInteractionService can create and show its associated VoiceInteractionSession.
 */
public final class AndroidAssistantInvocationContractTest {
    public static void main(String[] args) throws Exception {
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));

        check(smoke.contains("cmd role add-role-holder android.app.role.ASSISTANT"),
                "assistant smoke must still install JARVIS as Android's assistant role holder");
        check(smoke.contains("settings get secure voice_interaction_service"),
                "assistant smoke must still verify Android's selected voice-interaction service");
        check(smoke.contains("adb shell cmd voiceinteraction show"),
                "assistant smoke must invoke the selected service through VoiceInteractionManager");
        check(smoke.contains("JARVIS_SESSION_SERVICE_NEW_SESSION"),
                "assistant smoke must prove Android requested a JARVIS VoiceInteractionSession");
        check(smoke.contains("JARVIS_ASSISTANT_READY"),
                "assistant smoke must prove the JARVIS session content became ready");
        check(!smoke.contains("test \"$ASSISTANT_PASSED\" -eq 1"),
                "synthetic keyevent routing must not remain the sole blocking assistant gate");

        System.out.println("AndroidAssistantInvocationContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
