package com.jarvis.brain;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Direct reminder language must reach reasoning so structured temporal plans can be resolved before Android execution. */
public final class ReminderCortexRoutingContractTest {
    public static void main(String[] args) {
        naturalReminderReachesCortexForStructuredTemporalPlan();
        conditionalAlarmRequestReachesCortexInsteadOfImmediateReflex();
        System.out.println("ReminderCortexRoutingContractTest passed");
    }

    private static void naturalReminderReachesCortexForStructuredTemporalPlan() {
        AtomicInteger cortexCalls = new AtomicInteger();
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();
        ReasoningRouter router = request -> {
            cortexCalls.incrementAndGet();
            return new ReasoningResult(
                    "test-cortex",
                    "I'll prepare that reminder.",
                    new Plan("Remind me to call Mom",
                            List.of(new PlanStep("create_reminder",
                                    Map.of("title", "Call Mom", "start_millis", "1788382800000"),
                                    false))));
        };
        AssistantCore core = new AssistantCore(brain, router, ToolRegistry.standard());

        BrainResponse response = core.handle("Remind me to call Mom tomorrow at 5 PM");

        check(cortexCalls.get() == 1,
                "natural reminder requests must reach cortex so relative time becomes structured start_millis");
        check(response.kind() == BrainResponse.Kind.ACTION_PLAN,
                "a schema-valid structured reminder should return an executable action plan");
        check(response.plan() != null && response.plan().steps().size() == 1,
                "structured reminder should preserve exactly one reminder action");
        PlanStep step = response.plan().steps().get(0);
        check("create_reminder".equals(step.tool()), "structured reminder must use create_reminder");
        check("Call Mom".equals(step.arguments().get("title")), "structured reminder must preserve resolved title");
        check("1788382800000".equals(step.arguments().get("start_millis")),
                "structured reminder must preserve resolved epoch-millis time");
    }

    private static void conditionalAlarmRequestReachesCortexInsteadOfImmediateReflex() {
        AtomicInteger cortexCalls = new AtomicInteger();
        BrainEngine brain = BrainEngine.createDefault(Clock.systemUTC());
        brain.beginInvokedConversation();
        ReasoningRouter router = request -> {
            cortexCalls.incrementAndGet();
            return new ReasoningResult("test-cortex", "Cortex handled the conditional alarm request.", null);
        };
        AssistantCore core = new AssistantCore(brain, router, ToolRegistry.standard());

        BrainResponse response = core.handle("Set an alarm for 7 AM if I decide to wake up early.");

        check(cortexCalls.get() == 1,
                "conditional alarm language must reach cortex instead of firing an immediate local alarm reflex");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "conditional alarm language must not immediately produce an alarm action plan");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
