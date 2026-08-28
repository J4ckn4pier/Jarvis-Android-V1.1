package com.jarvis.brain;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExecutionRecoveryContractTest {
    private static int checks;

    public static void main(String[] args) {
        exhaustedRetryableFailureRequestsRecoveryWithoutReplayingCompletedSteps();
        hardFailureRemainsFailed();
        approvalBoundaryStillPrecedesRecoveryExecution();
        System.out.println("ExecutionRecoveryContractTest: " + checks + " assertions passed");
    }

    private static void exhaustedRetryableFailureRequestsRecoveryWithoutReplayingCompletedSteps() {
        ToolRegistry registry = new ToolRegistry();
        int[] firstCalls = {0};
        int[] flakyCalls = {0};
        registry.register(new ToolSpec("first", false, Set.of(), Set.of(), "first"), (args, ctx) -> {
            firstCalls[0]++;
            return ToolResult.success("first-ok");
        });
        registry.register(new ToolSpec("flaky", false, Set.of(), Set.of(), "flaky"), (args, ctx) -> {
            flakyCalls[0]++;
            return ToolResult.retryableFailure("network temporarily unavailable");
        });
        Plan plan = new Plan("do work", List.of(new PlanStep("first"), new PlanStep("flaky")));
        ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, new ApprovalGate());
        ExecutionCursor cursor = executor.start(plan);

        ExecutionReport report = executor.run(cursor, new ExecutionContext());
        check(report.status() == ExecutionReport.Status.RECOVERY_REQUIRED,
                "exhausted retryable failure should request recovery rather than collapse into hard failure");
        check(report.blockedTool().equals("flaky"), "recovery report should identify failed tool");
        check(report.failureDetail().contains("network temporarily unavailable"), "recovery report should preserve tool failure detail");
        check(cursor.nextStepIndex() == 1, "cursor must remain at failed step for safe recovery");
        check(firstCalls[0] == 1, "already-successful step must not be replayed when recovery is requested");
        check(flakyCalls[0] == 2, "retryable step should receive bounded immediate retry before recovery escalation");
    }

    private static void hardFailureRemainsFailed() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("hard", false, Set.of(), Set.of(), "hard"),
                (args, ctx) -> ToolResult.failure("permission permanently denied"));
        ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, new ApprovalGate());
        ExecutionReport report = executor.run(executor.start(new Plan("hard", List.of(new PlanStep("hard")))), new ExecutionContext());
        check(report.status() == ExecutionReport.Status.FAILED, "non-retryable failure should remain FAILED");
        check(report.failureDetail().contains("permission permanently denied"), "hard failure detail should be preserved");
    }

    private static void approvalBoundaryStillPrecedesRecoveryExecution() {
        ToolRegistry registry = new ToolRegistry();
        int[] calls = {0};
        registry.register(new ToolSpec("send_external", true, Set.of(), Set.of("message"), "send"), (args, ctx) -> {
            calls[0]++;
            return ToolResult.retryableFailure("service busy");
        });
        ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, new ApprovalGate());
        Plan plan = new Plan("send", List.of(new PlanStep("send_external", Map.of("message", "hello"), true)));
        ExecutionReport report = executor.run(executor.start(plan), new ExecutionContext());
        check(report.status() == ExecutionReport.Status.APPROVAL_REQUIRED,
                "consequential step must still require approval before any execution/recovery attempt");
        check(calls[0] == 0, "unapproved consequential tool must not run even once");
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
