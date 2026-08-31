package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps ordinary assistant reasoning local while requiring genuinely complex goals to reach orchestration. */
public final class AndroidLocalCortexPriorityContractTest {
    public static void main(String[] args) throws Exception {
        Path source = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        String code = Files.readString(source);
        check(code.contains("ReasoningRouter reasoning = selectiveReasoning(app, localReasoning);"),
                "reasoning must use the selective local-vs-remote routing boundary");
        check(code.contains("private static ReasoningRouter selectiveReasoning("),
                "runtime must expose a dedicated selective reasoning composition boundary");
        check(code.contains("private static boolean shouldDelegateComplexGoal("),
                "complex-goal delegation must be explicit and independently reviewable");
        check(code.contains("return localReasoning.reason(request);"),
                "ordinary reasoning must remain on the configured local/general cortex");
        check(code.contains("remoteReasoningOrLocal(app, localReasoning)"),
                "genuinely complex goals must retain the proven remote-goal submission path");
        check(code.contains("resumeRemoteGoalPresentation()"),
                "remote goal continuity must remain preserved as a separate capability");
        System.out.println("AndroidLocalCortexPriorityContractTest passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
