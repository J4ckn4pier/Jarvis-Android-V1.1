package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

public final class WakeWordSettingsTruthfulnessContractTest {
    public static void main(String[] args) throws Exception {
        Path mobile = Path.of("../android/app/src/main/java/com/jarvis/mobile");
        String settings = Files.readString(mobile.resolve("SettingsActivity.java"));
        String detector = Files.readString(mobile.resolve("assistant/AndroidOnDeviceWakeWordDetector.java"));

        check(settings.contains("SpeechRecognizer.isRecognitionAvailable(this)"),
                "Wake Word Settings must reflect Android speech-recognition availability instead of claiming listening solely from assistant-role state");
        check(settings.contains("Microphone permission required"),
                "Wake Word Settings must truthfully surface the microphone prerequisite used by production wake runtime");
        check(settings.contains("Android speech recognition unavailable"),
                "Wake Word Settings must truthfully surface a known runtime-unavailable state");
        check(settings.contains("Android offline speech support is verified at runtime"),
                "Wake Word Settings must not promise an active local listener before runtime verifies offline speech support");
        check(detector.contains("checkSelfPermission(Manifest.permission.RECORD_AUDIO)")
                        && detector.contains("SpeechRecognizer.isRecognitionAvailable(context)")
                        && detector.contains("EXTRA_PREFER_OFFLINE"),
                "Settings prerequisites must remain aligned with production passive-wake runtime");

        System.out.println("WakeWordSettingsTruthfulnessContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
