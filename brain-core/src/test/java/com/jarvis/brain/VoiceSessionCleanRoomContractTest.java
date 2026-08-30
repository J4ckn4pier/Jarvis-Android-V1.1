package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** VoiceInteractionSession is the popup overlay and must not retain donor image dependencies. */
public final class VoiceSessionCleanRoomContractTest {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));
        check(!source.contains("R.drawable.background_mk3"), "overlay must not use donor background");
        check(!source.contains("R.drawable.jarvis_normal"), "overlay must not use donor normal reactor");
        check(!source.contains("R.drawable.jarvis_active"), "overlay must not use donor active reactor");
        check(source.contains("RuntimeSurfacePresentation"), "overlay must remain bound to shared runtime presentation");
        check(source.contains("brain.approvePresentation") || source.contains("brain::approvePresentation"), "overlay must preserve approval action");
        check(source.contains("brain.retryPresentation") || source.contains("brain::retryPresentation"), "overlay must preserve recovery action");
        check(source.contains("brain.cancelPresentation") || source.contains("brain::cancelPresentation"), "overlay must preserve cancellation action");
        System.out.println("VoiceSessionCleanRoomContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
