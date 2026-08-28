package com.jarvis.brain;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExecutiveObservationLoopTest {
    private static int checks;

    public static void main(String[] args) {
        safeResearchCanExecuteAndReenterReasoningForSynthesis();
        consequentialStepStopsBeforeExecutionAndReturnsApprovalBoundary();
        loopStopsAtBoundedIterationCeiling();
        failedSafeToolBecomesObservationForRecoveryReasoning();
        System.out.println("ExecutiveObservationLoopTest: " + checks + " assertions passed");
    }

    private static void safeResearchCanExecuteAndReenterReasoningForSynthesis() {
        ToolRegistry registry = new ToolRegistry();
        int[] searches = {0};
        registry.register(new ToolSpec("search_places", false, Set.of(), Set.of("query"), "Search places"), (args, ctx) -> {
            searches[0]++;
            return ToolResult.success("Castle Cafe|Thai Basil|Union Bistro");
        });
        final int[] reasons = {0};
        final String[] secondContext = {""};
        ReasoningRouter router = request -> {
            reasons[0]++;
            if (reasons[0] == 1) return new ReasoningResult("local", "I'll look.",
                    new Plan("find dinner", List.of(new PlanStep("search_places", Map.of("query", "dinner tonight"), false))));
            secondContext[0] = request.context();
            return new ReasoningResult("local", "Castle Cafe looks like the best fit tonight.", null);
        };
        ExecutiveObservationLoop loop = new ExecutiveObservationLoop(router, registry, new ApprovalGate(), 4);
        ExecutiveOutcome outcome = loop.run("find me a place to eat for dinner tonight", "Castle Rock; tonight");
        check(outcome.status() == ExecutiveOutcome.Status.ANSWERED, "safe research should end in a synthesized answer");
        check(outcome.text().contains("Castle Cafe"), "final answer should come from post-tool synthesis");
        check(searches[0] == 1, "safe research tool should execute exactly once");
        check(reasons[0] == 2, "cortex should reason, observe tool result, then synthesize");
        check(secondContext[0].contains("search_places") && secondContext[0].contains("Castle Cafe"),
                "structured tool observation must be fed back into reasoning context");
    }

    private static void consequentialStepStopsBeforeExecutionAndReturnsApprovalBoundary() {
        ToolRegistry registry = new ToolRegistry();
        int[] sends = {0};
        registry.register(new ToolSpec("send_message", true, Set.of(), Set.of("recipient", "message"), "Send message"), (args, ctx) -> {
            sends[0]++;
            return ToolResult.success("sent");
        });
        ReasoningRouter router = request -> new ReasoningResult("local", "I can send that.",
                new Plan("send", List.of(new PlanStep("send_message", Map.of("recipient", "Mom", "message", "Running late"), false))));
        ExecutiveObservationLoop loop = new ExecutiveObservationLoop(router, registry, new ApprovalGate(), 3);
        ExecutiveOutcome outcome = loop.run("tell Mom I'm running late", "");
        check(outcome.status() == ExecutiveOutcome.Status.APPROVAL_REQUIRED, "consequential work must stop at approval boundary");
        check(outcome.pendingPlan() != null && outcome.pendingPlan().requiresApproval(), "pending validated plan should be returned for explicit approval");
        check(sends[0] == 0, "loop must never execute consequential tool before approval");
    }

    private static void loopStopsAtBoundedIterationCeiling() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("observe", false, Set.of(), Set.of(), "observe"), (args, ctx) -> ToolResult.success("same"));
        ReasoningRouter router = request -> new ReasoningResult("local", "again",
                new Plan("loop forever", List.of(new PlanStep("observe", Map.of(), false))));
        ExecutiveObservationLoop loop = new ExecutiveObservationLoop(router, registry, new ApprovalGate(), 2);
        ExecutiveOutcome outcome = loop.run("keep checking", "");
        check(outcome.status() == ExecutiveOutcome.Status.ITERATION_LIMIT, "agent loop needs a hard reasoning/action ceiling");
        check(outcome.iterations() == 2, "outcome should expose exact bounded iteration count");
        check(!outcome.text().isBlank() && !outcome.text().equals("again"),
                "iteration ceiling must never expose a useless looping fragment or silence");
        check(outcome.text().contains("same"),
                "iteration ceiling should preserve the best concrete tool evidence gathered so far");
        String lower = outcome.text().toLowerCase();
        check((lower.contains("couldn't") || lower.contains("could not") || lower.contains("unable") || lower.contains("not fully"))
                        && outcome.text().contains("?"),
                "iteration ceiling should state incomplete certainty and invite the user to choose the next check/context");
    }

    private static void failedSafeToolBecomesObservationForRecoveryReasoning() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("lookup", false, Set.of(), Set.of(), "lookup"),
                (args, ctx) -> ToolResult.failure("offline"));
        final int[] reasons = {0};
        final String[] recoveryContext = {""};
        ReasoningRouter router = request -> {
            reasons[0]++;
            if (reasons[0] == 1) return new ReasoningResult("local", "checking",
                    new Plan("lookup", List.of(new PlanStep("lookup", Map.of(), false))));
            recoveryContext[0] = request.context();
            return new ReasoningResult("local", "I can't verify that while the lookup source is offline.", null);
        };
        ExecutiveObservationLoop loop = new ExecutiveObservationLoop(router, registry, new ApprovalGate(), 3);
        ExecutiveOutcome outcome = loop.run("verify it", "");
        check(outcome.status() == ExecutiveOutcome.Status.ANSWERED, "safe tool failure may be reasoned about instead of dead-ending");
        check(recoveryContext[0].contains("FAILED") && recoveryContext[0].contains("offline"), "failure must become structured recovery observation");
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
