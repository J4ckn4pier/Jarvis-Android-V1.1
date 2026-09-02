package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: descriptive local-action language must not fire a reflex action. */
public final class DescriptiveVolumeOpeningSafetyTest {
    public static void main(String[] args) {
        descriptiveVolumeOpeningFallsThroughToCortex();
        descriptiveCommandShapedVolumeOpeningFallsThroughToCortex();
        descriptiveCalendarOpeningFallsThroughToCortex();
        descriptiveCommandShapedCalendarOpeningFallsThroughToCortex();
        descriptiveNotificationOpeningFallsThroughToCortex();
        descriptiveFlashlightCommandOpeningFallsThroughToCortex();
        System.out.println("DescriptiveVolumeOpeningSafetyTest passed");
    }

    private static void descriptiveVolumeOpeningFallsThroughToCortex() {
        AtomicInteger cortexCalls = new AtomicInteger();
        AssistantCore core = coreWithRouter(cortexCalls);
        BrainResponse response = core.handle("Volume control can make sound quieter automatically on some devices.");

        check(cortexCalls.get() == 1, "descriptive volume-opening language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive volume-opening language must not lower device volume");
    }

    private static void descriptiveCommandShapedVolumeOpeningFallsThroughToCortex() {
        AtomicInteger cortexCalls = new AtomicInteger();
        AssistantCore core = coreWithRouter(cortexCalls);
        BrainResponse response = core.handle("Turn the volume up can be useful in a noisy room.");

        check(cortexCalls.get() == 1, "descriptive command-shaped volume language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive command-shaped volume language must not change device volume");
    }

    private static void descriptiveCalendarOpeningFallsThroughToCortex() {
        AtomicInteger cortexCalls = new AtomicInteger();
        AssistantCore core = coreWithRouter(cortexCalls);
        BrainResponse response = core.handle("Calendar today is mostly digital instead of paper.");

        check(cortexCalls.get() == 1, "descriptive calendar-opening language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive calendar-opening language must not query the user's calendar");
    }

    private static void descriptiveCommandShapedCalendarOpeningFallsThroughToCortex() {
        AtomicInteger cortexCalls = new AtomicInteger();
        AssistantCore core = coreWithRouter(cortexCalls);
        BrainResponse response = core.handle("Show my calendar today can be useful when planning a trip.");

        check(cortexCalls.get() == 1, "descriptive command-shaped calendar language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive command-shaped calendar language must not query the user's calendar");
    }

    private static void descriptiveNotificationOpeningFallsThroughToCortex() {
        AtomicInteger cortexCalls = new AtomicInteger();
        AssistantCore core = coreWithRouter(cortexCalls);
        BrainResponse response = core.handle("Read notifications are useful when your hands are busy.");

        check(cortexCalls.get() == 1, "descriptive notification-opening language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive notification-opening language must not query notifications");
    }

    private static void descriptiveFlashlightCommandOpeningFallsThroughToCortex() {
        AtomicInteger cortexCalls = new AtomicInteger();
        AssistantCore core = coreWithRouter(cortexCalls);
        BrainResponse response = core.handle("Turn on the flashlight is useful advice during an outage.");

        check(cortexCalls.get() == 1, "descriptive flashlight command-shaped language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive flashlight command-shaped language must not change flashlight state");
    }

    private static AssistantCore coreWithRouter(AtomicInteger cortexCalls) {
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();
        ReasoningRouter router = request -> {
            cortexCalls.incrementAndGet();
            return new ReasoningResult("test-cortex", "Cortex handled it.", null);
        };
        return new AssistantCore(brain, router, ToolRegistry.standard());
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
