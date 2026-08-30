package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pins automatic acted-on episode creation to the actual shared execution boundary.
 * A platform caller must not have to remember to manufacture follow-up episodes after success.
 */
public final class ExecutedPlanFollowupBridgeTest {
    private static int checks;

    public static void main(String[] args) {
        completedPlanRecordsExactlyOneActedOnEpisode();
        approvalRecordsOnlyAfterConsequentialExecutionActuallyCompletes();
        failedPlanDoesNotRecordActedOnEpisode();
        episodeIdsStayUniqueAcrossRuntimeRestarts();
        pureResearchDoesNotPretendTheUserActed();
        mixedPlanAttributesEpisodeToLastActualAction();
        System.out.println("ExecutedPlanFollowupBridgeTest: " + checks + " assertions passed");
    }

    private static void completedPlanRecordsExactlyOneActedOnEpisode() {
        Instant now = Instant.parse("2026-08-29T23:50:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        ToolRegistry tools = new ToolRegistry();
        tools.register(new ToolSpec("zeta_action", false, Set.of(), Set.of(),
                "perform zeta action", ToolExecutionClass.DEVICE_REFLEX),
                (args, context) -> ToolResult.success("zeta complete"));
        List<RecommendationEpisode> episodes = new ArrayList<>();
        List<Instant> actedAt = new ArrayList<>();
        ActedOnEpisodeSink sink = (episode, at) -> { episodes.add(episode); actedAt.add(at); };
        Plan plan = new Plan("Complete zeta workflow", List.of(new PlanStep("zeta_action", Map.of(), false)));
        BrainRuntime runtime = runtime(tools, clock, sink, plan);

        BrainRuntime.Result result = runtime.handle("Jarvis, perform the zeta workflow");

        check(result.status() == BrainRuntime.Status.COMPLETED, "successful plan completes");
        check(episodes.size() == 1, "completed plan records exactly one acted-on episode");
        check("zeta_action".equals(episodes.get(0).domain()), "episode domain is the executed action tool");
        check("Complete zeta workflow".equals(episodes.get(0).subject()), "episode subject preserves plan goal");
        check(now.equals(episodes.get(0).recommendedAt()), "episode recommendation time uses runtime clock");
        check(now.equals(actedAt.get(0)), "acted-on time uses runtime clock at successful completion");
    }

    private static void approvalRecordsOnlyAfterConsequentialExecutionActuallyCompletes() {
        Instant now = Instant.parse("2026-08-29T23:51:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        ToolRegistry tools = new ToolRegistry();
        int[] executions = {0};
        tools.register(new ToolSpec("omega_action", true, Set.of(), Set.of(),
                "perform consequential omega action", ToolExecutionClass.CONSEQUENTIAL),
                (args, context) -> { executions[0]++; return ToolResult.success("omega complete"); });
        List<RecommendationEpisode> episodes = new ArrayList<>();
        ActedOnEpisodeSink sink = (episode, at) -> episodes.add(episode);
        Plan plan = new Plan("Complete omega workflow", List.of(new PlanStep("omega_action", Map.of(), true)));
        BrainRuntime runtime = runtime(tools, clock, sink, plan);

        BrainRuntime.Result blocked = runtime.handle("Jarvis, perform the omega workflow");
        check(blocked.status() == BrainRuntime.Status.APPROVAL_REQUIRED, "consequential plan waits for approval");
        check(executions[0] == 0, "consequential tool has not executed while blocked");
        check(episodes.isEmpty(), "approval-required plan is not falsely recorded as acted on");

        BrainRuntime.Result approved = runtime.approvePending();
        check(approved.status() == BrainRuntime.Status.COMPLETED, "approved plan completes");
        check(executions[0] == 1, "approved tool executes exactly once");
        check(episodes.size() == 1, "episode appears only after actual approved execution completes");
    }

    private static void failedPlanDoesNotRecordActedOnEpisode() {
        Instant now = Instant.parse("2026-08-29T23:52:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        ToolRegistry tools = new ToolRegistry();
        tools.register(new ToolSpec("broken_action", false, Set.of(), Set.of(),
                "fail safely", ToolExecutionClass.DEVICE_REFLEX),
                (args, context) -> ToolResult.failure("broken safely"));
        List<RecommendationEpisode> episodes = new ArrayList<>();
        ActedOnEpisodeSink sink = (episode, at) -> episodes.add(episode);
        Plan plan = new Plan("Attempt broken workflow", List.of(new PlanStep("broken_action", Map.of(), false)));
        BrainRuntime runtime = runtime(tools, clock, sink, plan);

        BrainRuntime.Result result = runtime.handle("Jarvis, attempt the broken workflow");
        check(result.status() != BrainRuntime.Status.COMPLETED, "failed tool does not produce completed plan status");
        check(episodes.isEmpty(), "failed plan never records an acted-on episode");
    }

    private static void episodeIdsStayUniqueAcrossRuntimeRestarts() {
        Instant now = Instant.parse("2026-08-29T23:53:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        ToolRegistry tools = new ToolRegistry();
        tools.register(new ToolSpec("restart_safe_action", false, Set.of(), Set.of(),
                "perform restart-safe action", ToolExecutionClass.DEVICE_REFLEX),
                (args, context) -> ToolResult.success("complete"));
        Plan plan = new Plan("Complete restart-safe workflow",
                List.of(new PlanStep("restart_safe_action", Map.of(), false)));
        List<RecommendationEpisode> first = new ArrayList<>();
        List<RecommendationEpisode> second = new ArrayList<>();

        runtime(tools, clock, (episode, at) -> first.add(episode), plan)
                .handle("Jarvis, run restart-safe workflow one");
        runtime(tools, clock, (episode, at) -> second.add(episode), plan)
                .handle("Jarvis, run restart-safe workflow two");

        check(first.size() == 1 && second.size() == 1, "both restarted runtimes record their completed action");
        check(!first.get(0).id().equals(second.get(0).id()),
                "episode ids must not collide when Android process/runtime restarts within the same millisecond");
    }

    private static void pureResearchDoesNotPretendTheUserActed() {
        Instant now = Instant.parse("2026-08-29T23:54:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        ToolRegistry tools = new ToolRegistry();
        tools.register(new ToolSpec("research_only", false, Set.of(), Set.of(),
                "research only", ToolExecutionClass.AUTONOMOUS_RESEARCH),
                (args, context) -> ToolResult.success("evidence found"));
        List<RecommendationEpisode> episodes = new ArrayList<>();
        Plan plan = new Plan("Compare dinner options", List.of(new PlanStep("research_only", Map.of(), false)));

        BrainRuntime.Result result = runtime(tools, clock, (episode, at) -> episodes.add(episode), plan)
                .handle("Jarvis, compare dinner options");

        check(result.status() == BrainRuntime.Status.COMPLETED, "pure research plan can complete normally");
        check(episodes.isEmpty(), "pure research must not be falsely marked as an acted-on real-world episode");
    }

    private static void mixedPlanAttributesEpisodeToLastActualAction() {
        Instant now = Instant.parse("2026-08-29T23:55:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        ToolRegistry tools = new ToolRegistry();
        tools.register(new ToolSpec("research_first", false, Set.of(), Set.of(),
                "research", ToolExecutionClass.AUTONOMOUS_RESEARCH),
                (args, context) -> ToolResult.success("research complete"));
        tools.register(new ToolSpec("actual_action", false, Set.of(), Set.of(),
                "real device action", ToolExecutionClass.DEVICE_REFLEX),
                (args, context) -> ToolResult.success("action complete"));
        tools.register(new ToolSpec("report_after", false, Set.of(), Set.of(),
                "report evidence", ToolExecutionClass.AUTONOMOUS_RESEARCH),
                (args, context) -> ToolResult.success("reported"));
        List<RecommendationEpisode> episodes = new ArrayList<>();
        Plan plan = new Plan("Research, act, then report", List.of(
                new PlanStep("research_first", Map.of(), false),
                new PlanStep("actual_action", Map.of(), false),
                new PlanStep("report_after", Map.of(), false)));

        BrainRuntime.Result result = runtime(tools, clock, (episode, at) -> episodes.add(episode), plan)
                .handle("Jarvis, research act and report");

        check(result.status() == BrainRuntime.Status.COMPLETED, "mixed plan completes");
        check(episodes.size() == 1, "mixed plan records one acted-on episode");
        check("actual_action".equals(episodes.get(0).domain()),
                "episode domain must identify the last actual action, not a trailing research/report step");
    }

    private static BrainRuntime runtime(ToolRegistry tools, Clock clock, ActedOnEpisodeSink sink, Plan plan) {
        BrainEngine engine = BrainEngine.createDefault(clock);
        AssistantCore assistant = new AssistantCore(engine,
                request -> new ReasoningResult("test-cortex", "working", plan), tools);
        return new BrainRuntime(assistant, tools, clock, sink);
    }

    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
