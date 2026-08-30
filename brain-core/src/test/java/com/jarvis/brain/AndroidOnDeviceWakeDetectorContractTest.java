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

        System.out.println("AndroidOnDeviceWakeDetectorContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
