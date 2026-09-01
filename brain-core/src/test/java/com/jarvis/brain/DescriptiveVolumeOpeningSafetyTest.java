package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: descriptive volume/sound language must not fire a local volume action. */
public final class DescriptiveVolumeOpeningSafetyTest {
    public static void main(String[] args) {
        AtomicInteger cortexCalls = new AtomicInteger();
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();
        ReasoningRouter router = request -> {
            cortexCalls.incrementAndGet();
            return new ReasoningResult("test-cortex", "Cortex handled it.", null);
        };
        AssistantCore core = new AssistantCore(brain, router, ToolRegistry.standard());

        BrainResponse response = core.handle("Volume control can make sound quieter automatically on some devices.");

        check(cortexCalls.get() == 1, "descriptive volume-opening language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive volume-opening language must not lower device volume");
        System.out.println("DescriptiveVolumeOpeningSafetyTest passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
