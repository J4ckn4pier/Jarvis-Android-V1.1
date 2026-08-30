package com.jarvis.brain;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ResumableExecutionTest {
    private static int passed;
    public static void main(String[] args) { approvalResumeDoesNotRepeatCompletedSteps(); resumeContinuesThroughRemainingStepsAfterApproval(); failedStepRetainsCursorForInspectionButDoesNotAdvance(); retryableConsequentialFailureReportsToolFailureDetail(); initialApprovalIsNotReportedAsFailure(); System.out.println("PASS " + passed + " resumable execution assertions"); }
    private static void approvalResumeDoesNotRepeatCompletedSteps() { int[] resolveCalls = {0}; int[] sendCalls = {0}; ToolRegistry registry = new ToolRegistry(); registry.register(new ToolSpec("resolve_contact", false, Set.of(), Set.of("name"), "resolve"), (a,c) -> { resolveCalls[0]++; return ToolResult.success("mom:+15551234567"); }); registry.register(new ToolSpec("send_message", true, Set.of(), Set.of("recipient","message"), "send"), (a,c) -> { sendCalls[0]++; return ToolResult.success("sent"); }); ApprovalGate gate = new ApprovalGate(); ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, gate); Plan plan = new Plan("text mom", List.of(new PlanStep("resolve_contact", Map.of("name","Mom"), false), new PlanStep("send_message", Map.of("recipient","Mom","message","Hi"), true))); ExecutionCursor cursor = executor.start(plan); ExecutionReport blocked = executor.run(cursor, new ExecutionContext()); check(blocked.status() == ExecutionReport.Status.APPROVAL_REQUIRED, "execution should stop at consequential step"); check(resolveCalls[0] == 1 && sendCalls[0] == 0, "pre-approval safe work should run exactly once"); gate.approve("send_message"); ExecutionReport done = executor.run(cursor, new ExecutionContext()); check(done.status() == ExecutionReport.Status.COMPLETED, "approved resume should finish"); check(resolveCalls[0] == 1, "resume must not repeat completed resolution step"); check(sendCalls[0] == 1, "approved consequential step should run once"); }
    private static void resumeContinuesThroughRemainingStepsAfterApproval() { int[] reportCalls = {0}; ToolRegistry registry = new ToolRegistry(); registry.register(new ToolSpec("send_message", true, Set.of(), Set.of("recipient","message"), "send"), (a,c) -> ToolResult.success("sent")); registry.register(new ToolSpec("report_outcome", false, Set.of(), Set.of(), "report"), (a,c) -> { reportCalls[0]++; return ToolResult.success("reported"); }); ApprovalGate gate = new ApprovalGate(); ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, gate); ExecutionCursor cursor = executor.start(new Plan("send", List.of(new PlanStep("send_message", Map.of("recipient","Mom","message","Hi"), true), new PlanStep("report_outcome", Map.of(), false)))); check(executor.run(cursor, new ExecutionContext()).status() == ExecutionReport.Status.APPROVAL_REQUIRED, "first run blocks"); gate.approve("send_message"); check(executor.run(cursor, new ExecutionContext()).status() == ExecutionReport.Status.COMPLETED, "resume should complete all remaining steps"); check(reportCalls[0] == 1, "post-action report should execute after approved step"); }
    private static void failedStepRetainsCursorForInspectionButDoesNotAdvance() { ToolRegistry registry = new ToolRegistry(); registry.register(new ToolSpec("lookup", false, Set.of(), Set.of(), "lookup"), (a,c) -> ToolResult.failure("offline")); ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, new ApprovalGate()); ExecutionCursor cursor = executor.start(new Plan("lookup", List.of(new PlanStep("lookup")))); ExecutionReport report = executor.run(cursor, new ExecutionContext()); check(report.status() == ExecutionReport.Status.FAILED, "hard failure should fail execution"); check(cursor.nextStepIndex() == 0, "failed step must remain current for recovery/inspection"); }
    private static void retryableConsequentialFailureReportsToolFailureDetail() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("prepare", false, Set.of(), Set.of(), "prepare"),
                (a,c) -> ToolResult.success("prepared"));
        registry.register(new ToolSpec("send_message", true, Set.of(), Set.of(), "send"),
                (a,c) -> ToolResult.retryableFailure("network unavailable"));
        ApprovalGate gate = new ApprovalGate();
        ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, gate);
        ExecutionCursor cursor = executor.start(new Plan("prepare then send", List.of(
                new PlanStep("prepare"), new PlanStep("send_message", Map.of(), true))));
        ExecutionReport initial = executor.run(cursor, new ExecutionContext());
        check(initial.status() == ExecutionReport.Status.APPROVAL_REQUIRED, "consequential step requires initial approval");
        gate.approve("send_message");
        ExecutionReport retry = executor.run(cursor, new ExecutionContext());
        check(retry.status() == ExecutionReport.Status.APPROVAL_REQUIRED, "retryable consequential failure requires fresh approval");
        check(retry.outputs().equals(List.of("prepared", "network unavailable")), "report preserves completed output and failed attempt output in order");
        check("network unavailable".equals(retry.failureDetail()), "failure detail must describe the failed attempt, not merely restate approval policy");
    }
    private static void initialApprovalIsNotReportedAsFailure() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("prepare", false, Set.of(), Set.of(), "prepare"),
                (a,c) -> ToolResult.success("prepared"));
        registry.register(new ToolSpec("send_message", true, Set.of(), Set.of(), "send"),
                (a,c) -> ToolResult.success("sent"));
        ResumablePlanExecutor executor = new ResumablePlanExecutor(registry, new ApprovalGate());
        ExecutionReport initial = executor.run(executor.start(new Plan("prepare then send", List.of(
                new PlanStep("prepare"), new PlanStep("send_message", Map.of(), true)))), new ExecutionContext());
        check(initial.status() == ExecutionReport.Status.APPROVAL_REQUIRED, "initial consequential attempt waits for approval");
        check(initial.outputs().equals(List.of("prepared")), "initial approval retains already completed safe work");
        check(initial.failureDetail().isBlank(), "initial approval is a policy pause, not a failure or retry");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); passed++; }
}
