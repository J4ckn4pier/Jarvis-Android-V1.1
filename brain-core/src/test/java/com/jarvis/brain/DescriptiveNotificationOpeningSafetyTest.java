package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: explanatory notification language must reach cortex instead of querying notifications. */
public final class DescriptiveNotificationOpeningSafetyTest {
    public static void main(String[] args) {
        AtomicInteger calls = new AtomicInteger();
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();
        ReasoningRouter router = request -> {
            calls.incrementAndGet();
            return new ReasoningResult("test-cortex", "Cortex handled it.", null);
        };
        AssistantCore core = new AssistantCore(brain, router, ToolRegistry.standard());

        BrainResponse response = core.handle("What notifications can reveal about privacy is worth understanding.");

        check(calls.get() == 1, "descriptive notification language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive notification language must not query notifications");
        System.out.println("DescriptiveNotificationOpeningSafetyTest passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
