package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class BrainAcceptanceTest {
    private static int passed = 0;

    public static void main(String[] args) {
        conversationRemainsActiveWithoutRepeatingWakeWord();
        howAreYouIsConversationNotUnsupported();
        naturalHelpIsDeterministicAndUseful();
        phoneAppResolvesToDialerTool();
        dinnerRequestBecomesResearchPlan();
        reservationCallRequiresApprovalAndDecomposes();
        unknownOpenEndedRequestFallsBackToReasoningNotNoFramework();
        conversationContextCarriesAcrossTurns();
        explicitSleepEndsConversationWindow();
        toolRegistryResolvesSemanticAliases();
        consequentialToolCannotRunWithoutApproval();
        approvedConsequentialToolRunsExactlyOnce();
        executorRetriesRecoverableFailure();
        System.out.println("PASS " + passed + " brain acceptance assertions");
    }

    private static BrainEngine engine() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        return BrainEngine.createDefault(clock);
    }

    private static void conversationRemainsActiveWithoutRepeatingWakeWord() {
        BrainEngine brain = engine();
        BrainResponse first = brain.handle("Hey Jarvis, how are you?");
        check(first.sessionActive(), "wake phrase should activate conversation");
        BrainResponse second = brain.handle("What have you been up to?");
        check(second.sessionActive(), "follow-up should remain in session");
        check(second.acceptedWithoutWakeWord(), "follow-up should not require Jarvis again");
    }

    private static void howAreYouIsConversationNotUnsupported() {
        BrainResponse response = engine().handle("Hey Jarvis, how are you?");
        check(response.kind() == BrainResponse.Kind.CONVERSATION, "how are you should be conversation");
        check(!response.text().toLowerCase().contains("framework"), "must never say no framework for ordinary conversation");
    }

    private static void naturalHelpIsDeterministicAndUseful() {
        BrainEngine brain = engine();
        brain.handle("Hey Jarvis");
        BrainResponse response = brain.handle("help me!!!");
        String text = response.text().toLowerCase();
        check(response.kind() == BrainResponse.Kind.CONVERSATION, "help should be handled locally as conversation");
        check(text.contains("speak naturally"), "help should explain natural-language interaction");
        check(text.contains("call contacts"), "help should advertise contact calling capability");
        check(text.contains("approval"), "help should explain consequential-action approval boundary");
    }

    private static void phoneAppResolvesToDialerTool() {
        BrainEngine brain = engine();
        brain.handle("Hey Jarvis");
        BrainResponse response = brain.handle("phone app");
        check(response.kind() == BrainResponse.Kind.ACTION_PLAN, "phone app should map to an action plan");
        check(response.plan().steps().stream().anyMatch(s -> s.tool().equals("open_dialer")), "phone app should resolve to dialer alias");
    }

    private static void dinnerRequestBecomesResearchPlan() {
        BrainEngine brain = engine();
        brain.handle("Hey Jarvis");
        BrainResponse response = brain.handle("find me a place to eat for dinner tonight");
        check(response.kind() == BrainResponse.Kind.ACTION_PLAN, "dinner request should become a plan");
        check(response.plan().steps().stream().anyMatch(s -> s.tool().equals("discover_places")), "dinner request should use place discovery");
        check(response.plan().steps().stream().anyMatch(s -> s.tool().equals("rank_options")), "dinner request should rank options");
    }

    private static void reservationCallRequiresApprovalAndDecomposes() {
        BrainEngine brain = engine();
        brain.handle("Hey Jarvis");
        BrainResponse response = brain.handle("Call the Castle Cafe in Castle Rock and tell them I would like a reservation for 5pm");
        Plan plan = response.plan();
        List<String> tools = plan.steps().stream().map(PlanStep::tool).toList();
        check(tools.contains("resolve_business"), "call plan should resolve business");
        check(tools.contains("place_conversational_call"), "call plan should contain conversational call tool");
        check(tools.contains("report_outcome"), "call plan should report back");
        check(plan.requiresApproval(), "speaking to a business on user's behalf requires approval");
        PlanStep call = plan.steps().stream().filter(s -> s.tool().equals("place_conversational_call")).findFirst().orElseThrow();
        check(call.arguments().get("goal").toLowerCase().contains("reservation"), "call tool should carry reservation goal");
        check(call.arguments().get("preferred_time").equals("5:00 PM"), "call plan should normalize 5pm");
    }

    private static void unknownOpenEndedRequestFallsBackToReasoningNotNoFramework() {
        BrainEngine brain = engine();
        brain.handle("Hey Jarvis");
        BrainResponse response = brain.handle("figure out the best way to make tomorrow less chaotic");
        check(response.kind() == BrainResponse.Kind.REASONING_REQUIRED, "open-ended goal should route to reasoning");
        check(!response.text().toLowerCase().contains("framework"), "unknown request should not produce no-framework failure");
    }

    private static void conversationContextCarriesAcrossTurns() {
        BrainEngine brain = engine();
        brain.handle("Hey Jarvis, I want Italian food tonight");
        BrainResponse response = brain.handle("find me somewhere good");
        check(response.contextSnapshot().toLowerCase().contains("italian"), "follow-up should carry recent user context");
    }

    private static void explicitSleepEndsConversationWindow() {
        BrainEngine brain = engine();
        brain.handle("Hey Jarvis");
        BrainResponse sleep = brain.handle("go to sleep");
        check(!sleep.sessionActive(), "sleep command should end conversation session");
        BrainResponse ambient = brain.handle("what time is it");
        check(ambient.kind() == BrainResponse.Kind.IGNORED_AMBIENT, "speech after sleep without wake should be ignored");
    }

    private static void toolRegistryResolvesSemanticAliases() {
        ToolRegistry registry = ToolRegistry.standard();
        check(registry.resolve("phone app").orElseThrow().name().equals("open_dialer"), "tool registry should resolve phone app alias");
        check(registry.resolve("telephone").orElseThrow().name().equals("open_dialer"), "tool registry should resolve telephone alias");
    }

    private static void consequentialToolCannotRunWithoutApproval() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("place_conversational_call", true, java.util.Set.of("call")),
                (args, ctx) -> ToolResult.success("called"));
        PlanExecutor executor = new PlanExecutor(registry, new ApprovalGate());
        Plan plan = new Plan("call", List.of(new PlanStep("place_conversational_call", java.util.Map.of(), true)));
        ExecutionReport report = executor.execute(plan, new ExecutionContext());
        check(report.status() == ExecutionReport.Status.APPROVAL_REQUIRED, "consequential action must stop for approval");
    }

    private static void approvedConsequentialToolRunsExactlyOnce() {
        final int[] calls = {0};
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("place_conversational_call", true, java.util.Set.of("call")),
                (args, ctx) -> { calls[0]++; return ToolResult.success("called"); });
        ApprovalGate gate = new ApprovalGate();
        gate.approve("place_conversational_call");
        PlanExecutor executor = new PlanExecutor(registry, gate);
        Plan plan = new Plan("call", List.of(new PlanStep("place_conversational_call", java.util.Map.of(), true)));
        ExecutionReport report = executor.execute(plan, new ExecutionContext());
        check(report.status() == ExecutionReport.Status.COMPLETED, "approved call should complete");
        check(calls[0] == 1, "approval should permit exactly one execution");
        ExecutionReport second = executor.execute(plan, new ExecutionContext());
        check(second.status() == ExecutionReport.Status.APPROVAL_REQUIRED, "approval token should be consumed");
    }

    private static void executorRetriesRecoverableFailure() {
        final int[] attempts = {0};
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("discover_places", false, java.util.Set.of()),
                (args, ctx) -> ++attempts[0] == 1 ? ToolResult.retryableFailure("temporary") : ToolResult.success("three choices"));
        PlanExecutor executor = new PlanExecutor(registry, new ApprovalGate());
        Plan plan = new Plan("dinner", List.of(new PlanStep("discover_places")));
        ExecutionReport report = executor.execute(plan, new ExecutionContext());
        check(report.status() == ExecutionReport.Status.COMPLETED, "executor should recover from retryable failure");
        check(attempts[0] == 2, "retryable tool should be retried once");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }
}