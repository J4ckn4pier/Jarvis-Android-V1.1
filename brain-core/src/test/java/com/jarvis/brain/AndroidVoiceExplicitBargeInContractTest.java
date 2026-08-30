package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Explicit LISTEN invocation must interrupt JARVIS speech before opening recognition. */
public final class AndroidVoiceExplicitBargeInContractTest {
    public static void main(String[] args) throws Exception {
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));

        check(session.contains("listen.setOnClickListener(v -> interruptSpeechAndListen())"),
                "LISTEN must use the explicit barge-in path rather than opening recognition under active TTS");
        check(session.contains("private void interruptSpeechAndListen()"),
                "voice session must define one explicit barge-in boundary");
        check(session.contains("resumeAfterSpeech = false;"),
                "barge-in must disarm the pending post-TTS auto-listen callback");
        check(session.contains("textToSpeech.stop();"),
                "barge-in must stop JARVIS speech immediately");
        check(session.contains("startListening();"),
                "barge-in must transition directly into recognition");

        System.out.println("AndroidVoiceExplicitBargeInContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
