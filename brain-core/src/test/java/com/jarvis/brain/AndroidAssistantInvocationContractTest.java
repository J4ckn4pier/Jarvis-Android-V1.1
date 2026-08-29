package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pins emulator session proof to the already system-bound JARVIS VoiceInteractionService without
 * relying on privileged shell-only VoiceInteractionManager commands or OEM hardware key routing.
 */
public final class AndroidAssistantInvocationContractTest {
    public static void main(String[] args) throws Exception {
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));
        String activity = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));
        String service = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceInteractionService.java"));

        check(smoke.contains("cmd role add-role-holder android.app.role.ASSISTANT"),
                "assistant smoke must still install JARVIS as Android's assistant role holder");
        check(smoke.contains("settings get secure voice_interaction_service"),
                "assistant smoke must still verify Android's selected voice-interaction service");
        check(!smoke.contains("adb shell cmd voiceinteraction show"),
                "emulator smoke must not rely on privileged shell VoiceInteractionManager commands");
        check(smoke.contains("jarvis_test_show_assistant"),
                "emulator smoke must request the debug-only app-owned session trigger");
        check(activity.contains("JarvisVoiceInteractionService.requestDebugTestSession(this)"),
                "debug activity trigger must delegate to the actual system-bound voice interaction service");
        check(service.contains("requestDebugTestSession(Context context)"),
                "voice interaction service must expose a debug-only session test hook");
        check(service.contains("ApplicationInfo.FLAG_DEBUGGABLE"),
                "session test hook must fail closed in non-debuggable production builds");
        check(service.contains("showSession("),
                "system-bound service must request its associated VoiceInteractionSession using the Android API");
        check(smoke.contains("JARVIS_SESSION_SERVICE_NEW_SESSION"),
                "assistant smoke must prove Android requested a JARVIS VoiceInteractionSession");
        check(smoke.contains("JARVIS_ASSISTANT_READY"),
                "assistant smoke must prove the JARVIS session content became ready");

        System.out.println("AndroidAssistantInvocationContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
