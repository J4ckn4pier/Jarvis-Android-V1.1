package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Samsung/OEM runtime microphone permission loss must stop retries instead of causing a recognizer loop. */
public final class AndroidVoicePermissionRevocationContractTest {
    public static void main(String[] args) throws Exception {
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));

        check(session.contains("error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS"),
                "voice recognition errors must explicitly detect microphone permission loss");
        check(session.contains("handleRecognitionPermissionLoss()"),
                "permission loss must route through a dedicated terminal recovery path");
        check(session.contains("private void handleRecognitionPermissionLoss()"),
                "voice session must define a permission-loss handler");
        check(session.contains("recognitionGeneration++;")
                        && session.contains("invalidateScheduledListen();")
                        && session.contains("releaseSpeechRecognizerSafely();"),
                "permission-loss recovery must invalidate callbacks, cancel pending relistens, and release the recognizer");
        check(session.contains("Microphone permission"),
                "permission loss must surface a truthful microphone-permission message");

        System.out.println("AndroidVoicePermissionRevocationContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
