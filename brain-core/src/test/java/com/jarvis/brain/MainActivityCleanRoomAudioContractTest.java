package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** The production Activity must not depend on the donor MP3 response player. */
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
        System.out.println("MainActivityCleanRoomAudioContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
