package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: command-shaped descriptive translation language must reach cortex instead of invoking translate. */
public final class DescriptiveTranslateOpeningSafetyTest {
    public static void main(String[] args) {
        AtomicInteger calls = new AtomicInteger();
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();
        ReasoningRouter router = request -> {
            calls.incrementAndGet();
            return new ReasoningResult("test-cortex", "Cortex handled it.", null);
        };
        AssistantCore core = new AssistantCore(brain, router, ToolRegistry.standard());

        BrainResponse response = core.handle("Translate text can be useful when traveling.");

        check(calls.get() == 1, "descriptive translate language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive translate language must not invoke the translate tool");
        System.out.println("DescriptiveTranslateOpeningSafetyTest passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
