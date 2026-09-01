package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: command-shaped descriptive call language must reach cortex instead of preparing a phone call. */
public final class DescriptiveCallOpeningSafetyTest {
    public static void main(String[] args) {
        AtomicInteger calls = new AtomicInteger();
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();
        ReasoningRouter router = request -> {
            calls.incrementAndGet();
            return new ReasoningResult("test-cortex", "Cortex handled it.", null);
        };
        AssistantCore core = new AssistantCore(brain, router, ToolRegistry.standard());

        BrainResponse response = core.handle("Call forwarding can be useful while traveling.");

        check(calls.get() == 1, "descriptive call language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive call language must not prepare a consequential call action");
        System.out.println("DescriptiveCallOpeningSafetyTest passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
