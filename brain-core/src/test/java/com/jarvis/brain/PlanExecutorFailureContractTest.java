package com.jarvis.brain;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fail-closed parity contract for the non-resumable executor. */
public final class PlanExecutorFailureContractTest {
    private static int checks;

    public static void main(String[] args) {
        approvedExceptionFailsClosedAndConsumesApproval();
        approvedNullResultFailsClosedAndConsumesApproval();
        retryableConsequentialFailureRequiresFreshApprovalBeforeRetry();
        successfulStatusWithoutOutputFailsClosed();
        System.out.println("PlanExecutorFailureContractTest: " + checks + " assertions passed");
    }

    private static void approvedExceptionFailsClosedAndConsumesApproval() {
        ToolRegistry registry = new ToolRegistry();
        ApprovalGate approvals = new ApprovalGate();
        int[] calls = {0};
        registry.register(new ToolSpec("send_external", true, Set.of(), Set.of("message"), "send"), (arguments, context) -> {
            calls[0]++;
            throw new RuntimeException("transport crashed");
        });
        PlanExecutor executor = new PlanExecutor(registry, approvals);
        Plan plan = new Plan("send", List.of(new PlanStep("send_external", Map.of("message", "hello"), true)));
        approvals.approve("send_external");
        ExecutionReport report;
        try {
            report = executor.execute(plan, new ExecutionContext());
        } catch (RuntimeException escaped) {
            throw new AssertionError("non-resumable executor must not leak tool exceptions", escaped);
        }
        check(report.status() == ExecutionReport.Status.FAILED, "tool exception should become FAILED");
        check(report.failureDetail().contains("transport crashed"), "failure detail should preserve the useful exception message");
        check(calls[0] == 1, "approved tool should execute once when it throws");
        ExecutionReport second = executor.execute(plan, new ExecutionContext());
        check(second.status() == ExecutionReport.Status.APPROVAL_REQUIRED,
                "thrown action must consume one-shot approval and cannot silently retry");
        check(calls[0] == 1, "failed consequential tool must not run again without fresh approval");
    }

    private static void approvedNullResultFailsClosedAndConsumesApproval() {
        ToolRegistry registry = new ToolRegistry();
        ApprovalGate approvals = new ApprovalGate();
        int[] calls = {0};
        registry.register(new ToolSpec("send_external", true, Set.of(), Set.of("message"), "send"), (arguments, context) -> {
            calls[0]++;
            return null;
        });
        PlanExecutor executor = new PlanExecutor(registry, approvals);
        Plan plan = new Plan("send", List.of(new PlanStep("send_external", Map.of("message", "hello"), true)));
        approvals.approve("send_external");
        ExecutionReport report;
        try {
            report = executor.execute(plan, new ExecutionContext());
        } catch (RuntimeException escaped) {
            throw new AssertionError("non-resumable executor must not leak null tool results", escaped);
        }
        check(report.status() == ExecutionReport.Status.FAILED, "null result should become FAILED");
        check(report.failureDetail().toLowerCase().contains("no result"), "null result should explain that the tool returned no result");
        check(calls[0] == 1, "approved tool should execute once when it returns null");
        ExecutionReport second = executor.execute(plan, new ExecutionContext());
        check(second.status() == ExecutionReport.Status.APPROVAL_REQUIRED,
                "null action must consume one-shot approval and cannot silently retry");
        check(calls[0] == 1, "null consequential tool must not run again without fresh approval");
    }

    private static void retryableConsequentialFailureRequiresFreshApprovalBeforeRetry() {
        ToolRegistry registry = new ToolRegistry();
        ApprovalGate approvals = new ApprovalGate();
        int[] calls = {0};
        registry.register(new ToolSpec("send_external", true, Set.of(), Set.of("message"), "send"), (arguments, context) -> {
            calls[0]++;
            return calls[0] == 1 ? ToolResult.retryableFailure("service busy") : ToolResult.success("sent");
        });
        PlanExecutor executor = new PlanExecutor(registry, approvals);
        Plan plan = new Plan("send", List.of(new PlanStep("send_external", Map.of("message", "hello"), true)));
        approvals.approve("send_external");
        ExecutionReport first = executor.execute(plan, new ExecutionContext());
        check(first.status() == ExecutionReport.Status.APPROVAL_REQUIRED,
                "a consequential retry must require a fresh approval before the second external attempt");
        check(calls[0] == 1, "one approval must authorize exactly one consequential execution attempt");
        ExecutionReport stillBlocked = executor.execute(plan, new ExecutionContext());
        check(stillBlocked.status() == ExecutionReport.Status.APPROVAL_REQUIRED,
                "retry remains blocked until a new approval token is granted");
        check(calls[0] == 1, "retryable consequential action must not silently run again");
        approvals.approve("send_external");
        ExecutionReport afterFreshApproval = executor.execute(plan, new ExecutionContext());
        check(afterFreshApproval.status() == ExecutionReport.Status.COMPLETED,
                "fresh approval should permit a new consequential attempt");
        check(calls[0] == 2, "fresh approval should authorize exactly the next attempt");
    }

    private static void successfulStatusWithoutOutputFailsClosed() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("lookup", false, Set.of(), Set.of(), "lookup"),
                (arguments, context) -> ToolResult.success(null));
        PlanExecutor executor = new PlanExecutor(registry, new ApprovalGate());
        ExecutionReport report;
        try {
            report = executor.execute(new Plan("lookup", List.of(new PlanStep("lookup"))), new ExecutionContext());
        } catch (RuntimeException escaped) {
            throw new AssertionError("success-with-null-output must not crash execution bookkeeping", escaped);
        }
        check(report.status() == ExecutionReport.Status.FAILED,
                "a tool cannot claim successful execution without returning a usable outcome");
        check(report.failureDetail().toLowerCase().contains("output"),
                "missing success output should be reported as an invalid tool outcome");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}