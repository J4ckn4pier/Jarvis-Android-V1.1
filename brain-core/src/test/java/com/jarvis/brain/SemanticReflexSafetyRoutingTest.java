package com.jarvis.brain;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/** Descriptive/ambiguous language must reach the cortex instead of firing imperative local reflexes. */
public final class SemanticReflexSafetyRoutingTest {
    public static void main(String[] args) {
        descriptiveOpenPhraseFallsThroughToCortex();
        descriptiveVolumePhraseFallsThroughToCortex();
        descriptiveFlashlightPhraseFallsThroughToCortex();
        descriptiveNotificationPhraseFallsThroughToCortex();
        descriptiveWeatherPhraseFallsThroughToCortex();
        descriptiveCalendarPhraseFallsThroughToCortex();
        directImperativeStillUsesSafeLocalReflex();
        System.out.println("SemanticReflexSafetyRoutingTest passed");
    }

    private static void descriptiveOpenPhraseFallsThroughToCortex() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);
        BrainResponse response = core.handle("What happens when I open Spotify while another song is playing?");
        check(calls.get() == 1, "descriptive open-app language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive open-app language must not become an action plan");
    }

    private static void descriptiveVolumePhraseFallsThroughToCortex() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);
        BrainResponse response = core.handle("Why is my volume lower after Bluetooth connects?");
        check(calls.get() == 1, "descriptive volume language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive volume language must not lower volume");
    }

    private static void descriptiveFlashlightPhraseFallsThroughToCortex() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);
        BrainResponse response = core.handle("Why does my flashlight turn off when the camera opens?");
        check(calls.get() == 1, "descriptive flashlight language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive flashlight language must not change flashlight state");
    }

    private static void descriptiveNotificationPhraseFallsThroughToCortex() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);
        BrainResponse response = core.handle("Why are my notifications delayed when battery saver is on?");
        check(calls.get() == 1, "descriptive notification language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive notification language must not query notifications");
    }

    private static void descriptiveWeatherPhraseFallsThroughToCortex() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);
        BrainResponse response = core.handle("Why does cold weather drain phone batteries faster?");
        check(calls.get() == 1, "descriptive weather language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive weather language must not trigger a weather lookup");
    }

    private static void descriptiveCalendarPhraseFallsThroughToCortex() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);
        BrainResponse response = core.handle("What calendar system is best for shared schedules?");
        check(calls.get() == 1, "descriptive calendar language must reach cortex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "descriptive calendar language must not query the user's calendar");
    }

    private static void directImperativeStillUsesSafeLocalReflex() {
        AtomicInteger calls = new AtomicInteger();
        AssistantCore core = coreWithRouter(calls);
        BrainResponse response = core.handle("Open Spotify");
        check(calls.get() == 0, "direct high-confidence imperative may stay local");
        check(response.kind() == BrainResponse.Kind.ACTION_PLAN, "direct app-open command should remain an action plan");
        check(response.plan() != null && !response.plan().steps().isEmpty()
                        && "open_app".equals(response.plan().steps().get(0).tool()),
                "direct app-open command should use open_app");
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