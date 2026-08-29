package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins speech-recognition confidence into the durable memory trust boundary. */
public final class AndroidVoiceMemoryIngestionContractTest {
    public static void main(String[] args) throws Exception {
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));

        check(runtime.contains("MemoryConsolidator memoryConsolidator"), "Android runtime must own memory consolidation for the durable store");
        check(runtime.contains("new MemoryConsolidator(new RuleMemoryExtractor(), memory)"), "runtime memory extraction must target the same durable memory source used by reasoning/UI");
        check(runtime.contains("handlePresentation(String utterance, double speechConfidence)"), "runtime must accept recognition confidence at the memory trust boundary");
        check(runtime.contains("memoryConsolidator.ingestUserTurn(utterance, speechConfidence"), "voice turns must use supplied confidence rather than being promoted blindly");

        check(session.contains("SpeechRecognizer.CONFIDENCE_SCORES"), "voice session must read recognizer confidence scores");
        check(session.contains("brain.handlePresentation(lastCommand, confidence)"), "voice session must forward top-hypothesis confidence to brain memory ingestion");
        check(!session.contains("brain.handlePresentation(lastCommand, 1.0"), "speech must never be hard-coded as fully trusted");

        System.out.println("AndroidVoiceMemoryIngestionContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
