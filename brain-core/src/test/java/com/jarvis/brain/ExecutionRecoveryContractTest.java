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
        recoveryOfConsequentialStepRequiresFreshApproval();
        approvedToolExceptionFailsClosedAndConsumesApproval();
        approvedNullToolResultFailsClosedAndConsumesApproval();
        System.out.println("ExecutionRecoveryContractTest: " + checks + " assertions passed");
    }

    private static void exhaustedRetryableFailureRequestsRecoveryWithoutReplayingCompletedSteps() {
        ToolRegistry registry = new ToolRegistry();
        int[] firstCalls = {0}; int[] flakyCalls = {0};
        registry.register(new ToolSpec("first", false, Set.of(), Set.of(), "first"), (args, ctx) -> { firstCalls[0]++; return ToolResult.success("first-ok"); });
        registry.register(new ToolSpec("flaky", false, Set.of(), Set.of(), "flaky"), (args, ctx) -> { flakyCalls[0]++; return ToolResult.retryableFailure("network temporarily unavailable"); });
        Plan plan = new Plan("do work", List.of(new PlanStep("first"), new PlanStep("flaky")));
        ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, new ApprovalGate());
        ExecutionCursor cursor = executor.start(plan);
        ExecutionReport report = executor.run(cursor, new ExecutionContext());
        check(report.status() == ExecutionReport.Status.RECOVERY_REQUIRED, "exhausted retryable failure should request recovery rather than collapse into hard failure");
        check(report.blockedTool().equals("flaky"), "recovery report should identify failed tool");
        check(report.failureDetail().contains("network temporarily unavailable"), "recovery report should preserve tool failure detail");
        check(cursor.nextStepIndex() == 1, "cursor must remain at failed step for safe recovery");
        check(firstCalls[0] == 1, "already-successful step must not be replayed when recovery is requested");
        check(flakyCalls[0] == 2, "retryable step should receive bounded immediate retry before recovery escalation");
    }

    private static void hardFailureRemainsFailed() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("hard", false, Set.of(), Set.of(), "hard"), (args, ctx) -> ToolResult.failure("permission permanently denied"));
        ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, new ApprovalGate());
        ExecutionReport report = executor.run(executor.start(new Plan("hard", List.of(new PlanStep("hard")))), new ExecutionContext());
        check(report.status() == ExecutionReport.Status.FAILED, "non-retryable failure should remain FAILED");
        check(report.failureDetail().contains("permission permanently denied"), "hard failure detail should be preserved");
    }

    private static void approvalBoundaryStillPrecedesRecoveryExecution() {
        ToolRegistry registry = new ToolRegistry(); int[] calls = {0};
        registry.register(new ToolSpec("send_external", true, Set.of(), Set.of("message"), "send"), (args, ctx) -> { calls[0]++; return ToolResult.retryableFailure("service busy"); });
        ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, new ApprovalGate());
        Plan plan = new Plan("send", List.of(new PlanStep("send_external", Map.of("message", "hello"), true)));
        ExecutionReport report = executor.run(executor.start(plan), new ExecutionContext());
        check(report.status() == ExecutionReport.Status.APPROVAL_REQUIRED, "consequential step must still require approval before any execution/recovery attempt");
        check(calls[0] == 0, "unapproved consequential tool must not run even once");
    }

    private static void recoveryOfConsequentialStepRequiresFreshApproval() {
        ToolRegistry registry = new ToolRegistry(); ApprovalGate approvals = new ApprovalGate(); int[] calls = {0};
        registry.register(new ToolSpec("send_external", true, Set.of(), Set.of("message"), "send"), (args, ctx) -> {
            calls[0]++;
            if (calls[0] <= 2) return ToolResult.retryableFailure("service busy");
            return ToolResult.success("sent");
        });
        ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, approvals);
        ExecutionCursor cursor = executor.start(new Plan("send", List.of(new PlanStep("send_external", Map.of("message", "hello"), true))));
        approvals.approve("send_external");
        ExecutionReport first = executor.run(cursor, new ExecutionContext());
        check(first.status() == ExecutionReport.Status.RECOVERY_REQUIRED, "approved consequential attempt may escalate to recovery after bounded transient failures");
        check(calls[0] == 2, "initial approval authorizes only the bounded initial attempt sequence");
        ExecutionReport withoutFreshApproval = executor.run(cursor, new ExecutionContext());
        check(withoutFreshApproval.status() == ExecutionReport.Status.APPROVAL_REQUIRED, "resume after recovery must require fresh approval");
        check(calls[0] == 2, "tool must not be touched again before fresh approval");
        approvals.approve("send_external");
        ExecutionReport afterFreshApproval = executor.run(cursor, new ExecutionContext());
        check(afterFreshApproval.status() == ExecutionReport.Status.COMPLETED, "fresh approval should permit resumed consequential attempt");
        check(calls[0] == 3, "fresh approval should authorize exactly the resumed attempt needed for success");
    }

    private static void approvedToolExceptionFailsClosedAndConsumesApproval() {
        ToolRegistry registry = new ToolRegistry(); ApprovalGate approvals = new ApprovalGate(); int[] calls = {0};
        registry.register(new ToolSpec("send_external", true, Set.of(), Set.of("message"), "send"), (args, ctx) -> {
            calls[0]++;
            throw new RuntimeException("transport crashed");
        });
        ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, approvals);
        ExecutionCursor cursor = executor.start(new Plan("send", List.of(new PlanStep("send_external", Map.of("message", "hello"), true))));
        approvals.approve("send_external");
        ExecutionReport first;
        try {
            first = executor.run(cursor, new ExecutionContext());
        } catch (RuntimeException escaped) {
            throw new AssertionError("tool exception must become a controlled failed execution report", escaped);
        }
        check(first.status() == ExecutionReport.Status.FAILED, "tool exception should fail closed");
        check(first.failureDetail().contains("transport crashed"), "controlled failure should preserve useful exception detail");
        check(calls[0] == 1, "approved action should be attempted only once when it throws");
        ExecutionReport withoutFreshApproval = executor.run(cursor, new ExecutionContext());
        check(withoutFreshApproval.status() == ExecutionReport.Status.APPROVAL_REQUIRED,
                "an exception must consume the one-shot approval so retry cannot occur silently");
        check(calls[0] == 1, "failed consequential action must not retry without fresh approval");
    }

    private static void approvedNullToolResultFailsClosedAndConsumesApproval() {
        ToolRegistry registry = new ToolRegistry(); ApprovalGate approvals = new ApprovalGate(); int[] calls = {0};
        registry.register(new ToolSpec("send_external", true, Set.of(), Set.of("message"), "send"), (args, ctx) -> {
            calls[0]++;
            return null;
        });
        ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, approvals);
        ExecutionCursor cursor = executor.start(new Plan("send", List.of(new PlanStep("send_external", Map.of("message", "hello"), true))));
        approvals.approve("send_external");
        ExecutionReport first;
        try {
            first = executor.run(cursor, new ExecutionContext());
        } catch (RuntimeException escaped) {
            throw new AssertionError("null tool result must become a controlled failed execution report", escaped);
        }
        check(first.status() == ExecutionReport.Status.FAILED, "null tool result should fail closed");
        check(first.failureDetail().toLowerCase().contains("no result"), "null failure should explain that no result was returned");
        check(calls[0] == 1, "approved action should be attempted only once when it returns null");
        ExecutionReport withoutFreshApproval = executor.run(cursor, new ExecutionContext());
        check(withoutFreshApproval.status() == ExecutionReport.Status.APPROVAL_REQUIRED,
                "null result must consume the one-shot approval so retry cannot occur silently");
        check(calls[0] == 1, "null consequential action must not retry without fresh approval");
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}