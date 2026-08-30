package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Full-screen microphone input must preserve recognizer confidence just like the assistant overlay. */
public final class AndroidMainActivityVoiceConfidenceContractTest {
    public static void main(String[] args) throws Exception {
        String activity = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));

        check(activity.contains("SpeechRecognizer.CONFIDENCE_SCORES"),
                "full-app speech results must read Android recognizer confidence scores");
        check(activity.contains("runCandidates(matches, scores)"),
                "speech candidates must carry confidence into command handling");
        check(activity.contains("runCommand(candidates.get(0), confidence)"),
                "the selected speech hypothesis must retain its measured confidence");
        check(activity.contains("runtime.handlePresentation(command, speechConfidence)"),
                "full-app speech must pass measured confidence to the shared approval/memory runtime");
        check(activity.contains("runCommand(command, 1.0)"),
                "explicit typed/debug command surfaces must remain explicitly trusted text input");

        System.out.println("AndroidMainActivityVoiceConfidenceContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
