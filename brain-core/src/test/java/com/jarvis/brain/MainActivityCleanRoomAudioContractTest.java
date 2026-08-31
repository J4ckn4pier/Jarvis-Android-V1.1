package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Production Activity audio must stay clean-room and honor the user-facing conversation settings. */
public final class MainActivityCleanRoomAudioContractTest {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));
        check(!source.contains("import com.jarvis.mobile.voice.LegacyResponsePlayer;"),
                "MainActivity must not import donor audio player");
        check(!source.contains("LegacyResponsePlayer legacyResponses"),
                "MainActivity must not own donor audio player");
        check(!source.contains("new LegacyResponsePlayer(this)"),
                "MainActivity must not instantiate donor audio player");
        check(!source.contains("legacyResponses.play("),
                "MainActivity must not execute donor audio cues");

        check(source.contains("UtteranceProgressListener"),
                "MainActivity must listen for TTS completion so continued conversation can resume listening");
        check(source.contains("getFloat(\"voice_rate\""),
                "MainActivity must apply the persisted Voice Model speech-rate setting");
        check(source.contains("getString(\"language\""),
                "MainActivity must apply the persisted Language setting");
        check(source.contains("onDone(String utteranceId)"),
                "MainActivity must react when JARVIS finishes speaking");
        check(source.contains("resumeListeningAfterSpeech"),
                "MainActivity must reopen listening after a spoken response for continued conversation");
        System.out.println("MainActivityCleanRoomAudioContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
