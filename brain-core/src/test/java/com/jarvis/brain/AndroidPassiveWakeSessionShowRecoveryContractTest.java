package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** A failed Android assistant-session handoff must not leave passive wake permanently dead. */
public final class AndroidPassiveWakeSessionShowRecoveryContractTest {
    public static void main(String[] args) throws Exception {
        String service = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceInteractionService.java"));
        check(service.contains("try {\n            showSession(new Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST);"),
                "wake session handoff must guard Android showSession failures");
        check(service.contains("catch (RuntimeException failure)"),
                "wake session handoff must catch OEM/platform runtime failures");
        check(service.contains("armPassiveWake(\"wake session show failed\")"),
                "failed wake session handoff must explicitly re-arm passive wake");
        System.out.println("AndroidPassiveWakeSessionShowRecoveryContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
