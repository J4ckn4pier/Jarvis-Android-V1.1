package com.jarvis.brain;

import java.time.Clock;

/** Generic conversation belongs to the general reasoning cortex, not canned phrase handlers. */
public final class GeneralCortexRoutingTest {
    public static void main(String[] args) {
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();

        BrainResponse response = brain.handle("tell me more about that");
        check(response.kind() == BrainResponse.Kind.REASONING_REQUIRED,
                "generic conversational follow-up must reach the general cortex, got " + response.kind());
        check(response.sessionActive(), "conversation must remain active while reasoning is delegated");
        check(response.acceptedWithoutWakeWord(), "follow-up must not require a second wake word");

        System.out.println("GeneralCortexRoutingTest passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
