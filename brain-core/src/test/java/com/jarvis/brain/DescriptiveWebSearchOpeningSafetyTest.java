package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: descriptive command-looking openings must reach cortex. */
public final class DescriptiveWebSearchOpeningSafetyTest {
    public static void main(String[] args) {
        descriptiveWebSearchOpeningFallsThroughToCortex();
        descriptiveNavigationDestinationFallsThroughToCortex();
        System.out.println("DescriptiveWebSearchOpeningSafetyTest passed");
    }

    private static void descriptiveWebSearchOpeningFallsThroughToCortex() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);

        BrainResponse response = core.handle("Search the web for information is a common research skill.");

        check(calls.get() == 1, "descriptive web-search-opening language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive web-search-opening language must not launch a web search");
    }

    private static void descriptiveNavigationDestinationFallsThroughToCortex() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);

        BrainResponse response = core.handle("Navigate to work is a phrase people use with voice assistants.");

        check(calls.get() == 1, "descriptive navigation-destination language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive navigation-destination language must not start navigation");
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
