package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: command-shaped descriptive message/notification language must reach cortex instead of firing actions. */
public final class DescriptiveMessageOpeningSafetyTest {
    public static void main(String[] args) {
        descriptiveMessageFallsThrough();
        descriptiveMessageNounPhraseFallsThrough();
        descriptiveNotificationQuestionFallsThrough();
        System.out.println("DescriptiveMessageOpeningSafetyTest passed");
    }

    private static void descriptiveMessageFallsThrough() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);

        BrainResponse response = core.handle("Message encryption can protect private conversations.");

        check(calls.get() == 1, "descriptive message language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive message language must not prepare a message action");
    }

    private static void descriptiveMessageNounPhraseFallsThrough() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);

        BrainResponse response = core.handle("Message encryption improves privacy for private conversations.");

        check(calls.get() == 1, "descriptive message noun phrase must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive message noun phrase must not prepare a message action");
    }

    private static void descriptiveNotificationQuestionFallsThrough() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);

        BrainResponse response = core.handle("What notifications can reveal about privacy is worth understanding.");

        check(calls.get() == 1, "descriptive notification language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive notification language must not query notifications");
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
