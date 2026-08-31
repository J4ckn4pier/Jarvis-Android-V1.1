package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Ordinary conversation must use the configured cortex; the background orchestrator must not hijack every unhandled utterance. */
public final class AndroidConversationalCortexPriorityContractTest {
    public static void main(String[] args) throws Exception {
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));
        check(runtime.contains("ReasoningRouter reasoning = request -> reasonWithConfiguredCortex(app, request, tools)"),
                "ordinary reasoning must route directly to the configured conversational cortex");
        check(!runtime.contains("ReasoningRouter reasoning = remoteReasoningOrLocal(app, localReasoning)"),
                "remote background goals must not wrap ordinary conversational reasoning");
        check(runtime.contains("resumeRemoteGoalPresentation"),
                "background-goal resume support must remain available as a separate capability");
        System.out.println("AndroidConversationalCortexPriorityContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
