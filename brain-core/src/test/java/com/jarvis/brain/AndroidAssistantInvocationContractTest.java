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
        String receiver = Files.readString(Path.of("../android/app/src/debug/java/com/jarvis/mobile/assistant/JarvisAssistantTestReceiver.java"));
        String debugManifest = Files.readString(Path.of("../android/app/src/debug/AndroidManifest.xml"));
        String service = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceInteractionService.java"));
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));
        String managedSession = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/ManagedJarvisVoiceSession.java"));

        check(smoke.contains("cmd role add-role-holder android.app.role.ASSISTANT"),
                "assistant smoke must still install JARVIS as Android's assistant role holder");
        check(smoke.contains("settings get secure voice_interaction_service"),
                "assistant smoke must still verify Android's selected voice-interaction service");
        check(!smoke.contains("adb shell cmd voiceinteraction show"),
                "emulator smoke must not rely on privileged shell VoiceInteractionManager commands");
        check(smoke.contains("com.jarvis.mobile.DEBUG_SHOW_ASSISTANT"),
                "emulator smoke must request the debug-only app-owned session trigger");
        check(receiver.contains("JarvisVoiceInteractionService.requestDebugTestSession(context)"),
                "debug receiver must delegate to the actual system-bound voice interaction service");
        check(debugManifest.contains("JarvisAssistantTestReceiver"),
                "debug session receiver must live only in the debug manifest/source set");
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
        check(session.contains("catch (RuntimeException lockStateFailure)")
                        && session.contains("Lock-screen state probe failed; blocking Assistant session")
                        && session.contains("JARVIS_LOCK_SCREEN_BLOCKED"),
                "Samsung/OEM Keyguard Binder failures must fail closed through the normal blocked lock-screen Assistant path instead of escaping onShow");
        check(managedSession.contains("try {\n            super.onHide();\n        } finally {\n            JarvisVoiceInteractionService.rearmPassiveWakeAfterSession();"),
                "a lock-screen-blocked Assistant hide must still re-arm passive Jarvis wake even if session cleanup encounters an OEM lifecycle failure");

        System.out.println("AndroidAssistantInvocationContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
