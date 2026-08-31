package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Prevents remote background-goal orchestration from hijacking ordinary assistant conversation. */
public final class AndroidLocalCortexPriorityContractTest {
    public static void main(String[] args) throws Exception {
        Path source = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        String code = Files.readString(source);
        check(code.contains("ReasoningRouter reasoning = localReasoning;"),
                "ordinary reasoning must use the configured local/general cortex directly");
        check(!code.contains("ReasoningRouter reasoning = remoteReasoningOrLocal(app, localReasoning);"),
                "remote goal submission must not be the default conversational reasoning path");
        check(code.contains("resumeRemoteGoalPresentation()"),
                "remote goal continuity must remain preserved as a separate capability");
        System.out.println("AndroidLocalCortexPriorityContractTest passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
