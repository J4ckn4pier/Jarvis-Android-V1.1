package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: command-shaped descriptive message language must reach cortex instead of preparing a text. */
public final class DescriptiveMessageOpeningSafetyTest {
    public static void main(String[] args) {
        AtomicInteger calls = new AtomicInteger();
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();
        ReasoningRouter router = request -> {
            calls.incrementAndGet();
            return new ReasoningResult("test-cortex", "Cortex handled it.", null);
        };
        AssistantCore core = new AssistantCore(brain, router, ToolRegistry.standard());

        BrainResponse response = core.handle("Message encryption can protect private conversations.");

        check(calls.get() == 1, "descriptive message language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive message language must not prepare a message action");
        System.out.println("DescriptiveMessageOpeningSafetyTest passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
