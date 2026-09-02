package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: sentence-opening phone prose must not be mistaken for an open-dialer command. */
public final class DescriptiveDialerOpeningSafetyTest {
    public static void main(String[] args) {
        AtomicInteger cortexCalls = new AtomicInteger();
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();
        ReasoningRouter router = request -> {
            cortexCalls.incrementAndGet();
            return new ReasoningResult("test-cortex", "Cortex handled it.", null);
        };
        AssistantCore core = new AssistantCore(brain, router, ToolRegistry.standard());

        BrainResponse response = core.handle("Phone calls can be distracting while driving.");
        check(cortexCalls.get() == 1, "descriptive phone-opening language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive phone-opening language must not open the dialer");

        System.out.println("DescriptiveDialerOpeningSafetyTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
