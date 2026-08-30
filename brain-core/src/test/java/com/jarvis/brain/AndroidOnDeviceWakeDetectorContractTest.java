package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** APK sprint: passive wake must use a real local Android recognizer path, never the disabled stub alone. */
public final class AndroidOnDeviceWakeDetectorContractTest {
    public static void main(String[] args) throws Exception {
        Path assistant = Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant");
        String factory = Files.readString(assistant.resolve("AndroidWakeWordDetectorFactory.java"));
        Path detectorPath = assistant.resolve("AndroidOnDeviceWakeWordDetector.java");
        check(Files.exists(detectorPath), "production must include a real Android on-device wake detector");
        String detector = Files.readString(detectorPath);
        String service = Files.readString(assistant.resolve("JarvisVoiceInteractionService.java"));
        String session = Files.readString(assistant.resolve("JarvisVoiceSession.java"));

        check(factory.contains("AndroidOnDeviceWakeWordDetector.isAvailable(app)"),
                "factory must prefer an Android on-device detector when the platform exposes one");
        check(factory.contains("new AndroidOnDeviceWakeWordDetector(app)"),
                "factory must construct the real on-device detector instead of returning the disabled stub");
        check(detector.contains("SpeechRecognizer.isOnDeviceRecognitionAvailable(context)"),
                "wake detector must prove the recognizer is on-device before starting passive audio");
        check(detector.contains("SpeechRecognizer.createOnDeviceSpeechRecognizer(context)"),
                "wake detector must use the Android on-device recognizer");
        check(!detector.contains("SpeechRecognizer.createSpeechRecognizer(context)"),
                "passive wake must never silently fall back to a potentially network recognizer");
        check(detector.contains("hey jarvis") && detector.contains("jarvis"),
                "wake detector must recognize both requested invocation forms");
        check(detector.contains("startListening") && detector.contains("RecognitionListener"),
                "wake detector must actually listen instead of reporting a ready stub");
        check(detector.contains("postDelayed") || detector.contains("startListening(intent)"),
                "wake detector must continue listening after a non-wake utterance");

        // The passive recognizer and the active conversation recognizer must never fight over the
        // microphone. A wake match intentionally releases the passive recognizer; side-key/default
        // assistant invocation must do the same, and closing the assistant must arm wake again so
        // Jarvis/Hey Jarvis works more than once per service lifetime.
        check(service.contains("pausePassiveWakeForSession"),
                "voice service must expose a session hook that pauses passive wake while the assistant is active");
        check(service.contains("rearmPassiveWakeAfterSession"),
                "voice service must expose a session hook that re-arms passive wake after the assistant closes");
        check(session.contains("JarvisVoiceInteractionService.pausePassiveWakeForSession()"),
                "assistant session must pause passive wake when shown to avoid microphone contention");
        check(session.contains("JarvisVoiceInteractionService.rearmPassiveWakeAfterSession()"),
                "assistant session must re-arm passive wake when hidden so a second wake phrase can work");

        System.out.println("AndroidOnDeviceWakeDetectorContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
