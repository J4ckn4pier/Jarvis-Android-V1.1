package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AssistantExecutiveIntegrationTest {
    private static int checks;

    public static void main(String[] args) {
        assistantCoreExecutesSafeSemanticPlanAndSynthesizesResult();
        synthesisStateDeltaSurvivesIntoNextTurn();
        assistantCoreStillStopsBeforeConsequentialProviderPlan();
        System.out.println("AssistantExecutiveIntegrationTest: " + checks + " assertions passed");
    }

    private static ToolRegistry dinnerRegistry(int[] searches) {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("discover_places", false, Set.of(), Set.of("category"), "Discover places", ToolExecutionClass.AUTONOMOUS_RESEARCH), (args, ctx) -> {
            searches[0]++;
            return ToolResult.success("Castle Cafe|open_status=unknown|distance=0.4mi");
        });
        registry.register(new ToolSpec("rank_options", false, Set.of(), Set.of(), "Rank options", ToolExecutionClass.AUTONOMOUS_RESEARCH),
                (args, ctx) -> ToolResult.success("Castle Cafe ranked first"));
        registry.register(new ToolSpec("present_options", false, Set.of(), Set.of(), "Present options", ToolExecutionClass.AUTONOMOUS_RESEARCH),
                (args, ctx) -> ToolResult.success("Castle Cafe"));
        return registry;
    }

    private static void assistantCoreExecutesSafeSemanticPlanAndSynthesizesResult() {
        int[] searches = {0};
        ToolRegistry registry = dinnerRegistry(searches);
        int[] reasons = {0};
        ReasoningRouter router = request -> {
            reasons[0]++;
            check(request.context().contains("TOOL_OBSERVATION") && request.context().contains("Castle Cafe"),
                    "tool evidence should be present when the cortex synthesizes the final answer");
            return new ReasoningResult("local", "Castle Cafe is the closest result I found; its current open status is still unverified.", null);
        };
        AssistantCore core = new AssistantCore(
                BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-28T04:00:00Z"), ZoneOffset.UTC)),
                router, registry);
        core.handle("Hey Jarvis");
        BrainResponse response = core.handle("find me somewhere to eat for dinner tonight");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "safe semantic plan should execute inside the executive loop instead of escaping as ACTION_PLAN");
        check(response.text().contains("Castle Cafe") && response.text().contains("unverified"),
                "assistant should return the post-tool synthesized answer");
        check(searches[0] == 1, "safe discovery tool should execute exactly once through AssistantCore");
        check(reasons[0] == 1, "semantic plan should use the cortex only after tool observations for final synthesis");
    }

    private static void synthesisStateDeltaSurvivesIntoNextTurn() {
        int[] searches = {0};
        ToolRegistry registry = dinnerRegistry(searches);
        int[] reasons = {0};
        ReasoningRouter router = request -> {
            reasons[0]++;
            if (reasons[0] == 1) {
                return new ReasoningResult("local", "Castle Cafe is my current recommendation.", null,
                        new SessionStateDelta("find dinner", "dinner options", Map.of("candidate", "Castle Cafe"),
                                "", "", "Castle Cafe is the current best option", false));
            }
            check(request.context().contains("[SESSION_DECISION] Castle Cafe is the current best option"),
                    "post-tool synthesis state must survive into the next reasoning turn");
            return new ReasoningResult("local", "I still have Castle Cafe as the current best option.", null);
        };
        AssistantCore core = new AssistantCore(
                BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-28T04:00:00Z"), ZoneOffset.UTC)),
                router, registry);
        core.handle("Hey Jarvis");
        core.handle("find me somewhere to eat for dinner tonight");
        BrainResponse followup = core.handle("compare that choice against another option");
        check(followup.kind() == BrainResponse.Kind.CONVERSATION, "follow-up should still reason conversationally");
        check(reasons[0] == 2, "follow-up should make one additional reasoning call");
    }

    private static void assistantCoreStillStopsBeforeConsequentialProviderPlan() {
        ToolRegistry registry = new ToolRegistry();
        int[] sends = {0};
        registry.register(new ToolSpec("send_message", true, Set.of(), Set.of("recipient", "message"), "Send message"), (args, ctx) -> {
            sends[0]++;
            return ToolResult.success("sent");
        });
        ReasoningRouter router = request -> new ReasoningResult("local", "I can send that when you approve.",
                new Plan("send message", List.of(new PlanStep("send_message", Map.of("recipient", "Mom", "message", "Running late"), true))));
        AssistantCore core = new AssistantCore(
                BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-28T04:00:00Z"), ZoneOffset.UTC)),
                router, registry);
        core.handle("Hey Jarvis");
        BrainResponse response = core.handle("tell Mom I'm running late");
        check(response.kind() == BrainResponse.Kind.ACTION_PLAN && response.plan() != null,
                "consequential provider plan must remain an explicit approval-boundary action plan");
        check(sends[0] == 0, "AssistantCore executive integration must not execute consequential tools autonomously");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
