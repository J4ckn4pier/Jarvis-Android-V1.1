package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: command-shaped descriptive media language must reach cortex instead of starting playback. */
public final class DescriptiveMediaPlaySafetyTest {
    public static void main(String[] args) {
        AtomicInteger calls = new AtomicInteger();
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();
        ReasoningRouter router = request -> {
            calls.incrementAndGet();
            return new ReasoningResult("test-cortex", "Cortex handled it.", null);
        };
        AssistantCore core = new AssistantCore(brain, router, ToolRegistry.standard());

        BrainResponse response = core.handle("Play music can help people focus while they work.");

        check(calls.get() == 1, "descriptive play-music language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive play-music language must not start media playback");
        System.out.println("DescriptiveMediaPlaySafetyTest passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
