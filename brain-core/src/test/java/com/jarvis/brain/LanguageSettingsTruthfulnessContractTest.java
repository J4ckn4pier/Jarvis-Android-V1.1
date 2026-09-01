package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

public final class LanguageSettingsTruthfulnessContractTest {
    public static void main(String[] args) throws Exception {
        Path mobile = Path.of("../android/app/src/main/java/com/jarvis/mobile");
        String settings = Files.readString(mobile.resolve("SettingsActivity.java"));
        String voiceSession = Files.readString(mobile.resolve("assistant/JarvisVoiceSession.java"));

        check(settings.contains("Speech recognition & spoken response language"),
                "Language Settings must truthfully explain that this control changes speech recognition and spoken-response locale, not the reasoning language");
        check(settings.contains("This changes Android speech recognition and text-to-speech only; it does not force the AI provider to answer in that language."),
                "Language Settings must not imply the provider's reasoning/output language is controlled by the Android speech locale");
        check(settings.contains("putString(\"language\", tags[index])"),
                "Language selection must persist only on SAVE");
        check(settings.contains("setNegativeButton(\"CANCEL\",null)"),
                "Language selection must preserve CANCEL semantics");
        check(voiceSession.contains("configuredLanguage().toLanguageTag()")
                        && voiceSession.contains("textToSpeech.setLanguage(configuredLanguage())"),
                "the saved Language setting must remain consumed by production speech recognition and TTS runtime");

        System.out.println("LanguageSettingsTruthfulnessContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
