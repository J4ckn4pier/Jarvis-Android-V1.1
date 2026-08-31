package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Descriptive/ambiguous language must reach the cortex instead of firing imperative local reflexes. */
public final class SemanticReflexSafetyRoutingTest {
    public static void main(String[] args) {
        descriptiveOpenPhraseFallsThroughToCortex();
        descriptiveVolumePhraseFallsThroughToCortex();
        directImperativeStillUsesSafeLocalReflex();
        System.out.println("SemanticReflexSafetyRoutingTest passed");
    }

    private static void descriptiveOpenPhraseFallsThroughToCortex() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);
        BrainResponse response = core.handle("What happens when I open Spotify while another song is playing?");
        check(calls.get() == 1, "descriptive open-app language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive open-app language must not become an action plan");
    }

    private static void descriptiveVolumePhraseFallsThroughToCortex() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);
        BrainResponse response = core.handle("Why is my volume lower after Bluetooth connects?");
        check(calls.get() == 1, "descriptive volume language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive volume language must not lower volume");
    }

    private static void directImperativeStillUsesSafeLocalReflex() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);
        BrainResponse response = core.handle("Open Spotify");
        check(calls.get() == 0, "direct high-confidence imperative may stay local");
        check(response.kind() == BrainResponse.Kind.ACTION_PLAN, "direct app-open command should remain an action plan");
        check(response.plan() != null && !response.plan().steps().isEmpty()
                        && "open_app".equals(response.plan().steps().get(0).tool()),
                "direct app-open command should use open_app");
    }

    private static AssistantCore coreWithRouter(AtomicInteger calls) {
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();
        ReasoningRouter router = request -> {
            calls.incrementAndGet();
            return new ReasoningResult("test-cortex", "Cortex handled it.", null);
        };
        return new AssistantCore(brain, router, ToolRegistry.standard());
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
