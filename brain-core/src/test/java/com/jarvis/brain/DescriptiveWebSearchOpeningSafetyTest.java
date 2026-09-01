package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: descriptive web-search-opening language must reach cortex. */
public final class DescriptiveWebSearchOpeningSafetyTest {
    public static void main(String[] args) {
        AtomicInteger calls = new AtomicInteger();
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();
        ReasoningRouter router = request -> {
            calls.incrementAndGet();
            return new ReasoningResult("test-cortex", "Cortex handled it.", null);
        };
        AssistantCore core = new AssistantCore(brain, router, ToolRegistry.standard());

        BrainResponse response = core.handle("Search the web for information is a common research skill.");

        check(calls.get() == 1, "descriptive web-search-opening language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive web-search-opening language must not launch a web search");
        System.out.println("DescriptiveWebSearchOpeningSafetyTest passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
