package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Ensures Android's real shared runtime automatically feeds successful executed plans into durable follow-up state. */
public final class AndroidExecutedPlanFollowupCompositionContractTest {
    public static void main(String[] args) throws Exception {
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        check(Files.exists(runtimePath), "Android production runtime source must exist");
        String runtime = Files.readString(runtimePath);

        check(runtime.contains("followups = new OutcomeFollowupRuntime"),
                "Android runtime must own the privacy-gated durable follow-up runtime");
        check(runtime.contains("new BrainRuntime(assistant, tools, clock, followups::recordActedOn)"),
                "Android shared execution boundary must automatically record completed plans into follow-up runtime");
        check(!runtime.contains("new BrainRuntime(assistant, tools);"),
                "Android production must not silently use the no-op follow-up sink constructor");

        System.out.println("AndroidExecutedPlanFollowupCompositionContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
