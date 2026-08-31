package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android/OEM wake handoff and startup failures must not leave passive wake permanently dead. */
public final class AndroidPassiveWakeSessionShowRecoveryContractTest {
    public static void main(String[] args) throws Exception {
        String service = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceInteractionService.java"));
        check(service.contains("try {\n            showSession(new Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST);"),
                "wake session handoff must guard Android showSession failures");
        check(service.contains("catch (RuntimeException failure)"),
                "wake session handoff must catch OEM/platform runtime failures");
        check(service.contains("armPassiveWake(\"wake session show failed\")"),
                "failed wake session handoff must explicitly re-arm passive wake");

        check(service.contains("PASSIVE_WAKE_RETRY_DELAY_MS"),
                "passive wake startup must define a bounded retry delay for transient OEM recognizer failures");
        check(service.contains("main.postDelayed(passiveWakeRetry, PASSIVE_WAKE_RETRY_DELAY_MS)"),
                "a failed passive wake start must schedule another start attempt instead of remaining permanently deaf");
        check(service.contains("main.removeCallbacks(passiveWakeRetry)"),
                "passive wake retry must be cancellable during session handoff, disable, and shutdown");
        System.out.println("AndroidPassiveWakeSessionShowRecoveryContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
