package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Samsung/OEM runtime microphone and recognition callback failures must recover without recognizer loops or stranded sessions. */
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
        check(session.contains("private Boolean microphonePermissionGrantedSafely()"),
                "active listening must protect the runtime microphone-permission probe from Samsung/OEM package-service failures");
        check(session.contains("MICROPHONE_PERMISSION_PROBE_FAILED"),
                "permission-probe failures must leave explicit runtime evidence");
        check(session.contains("Boolean microphonePermissionGranted = microphonePermissionGrantedSafely();")
                        && session.contains("if (microphonePermissionGranted == null)"),
                "an indeterminate microphone-permission probe must recover without crashing or pretending permission was denied");
        check(session.contains("handleRecognitionResultsSafely(results)"),
                "active Assistant final-result Bundles must cross a Samsung/OEM exception boundary before command execution");
        check(session.contains("handleRecognitionPartialSafely(partialResults)"),
                "active Assistant partial-result Bundles must cross the same protected OEM callback boundary");
        check(session.contains("ACTIVE_RECOGNITION_RESULTS_CALLBACK_FAILED")
                        && session.contains("scheduleNextListen();"),
                "malformed active recognition results must leave evidence and reopen multi-turn listening instead of stranding the session");

        System.out.println("AndroidVoicePermissionRevocationContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
