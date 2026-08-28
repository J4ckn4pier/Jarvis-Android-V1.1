package com.jarvis.brain;

public final class ExecutiveTaskControlTest {
    private static int checks;

    public static void main(String[] args) {
        sameGoalCorrectionReplacesCurrentGoal();
        contextualRefinementKeepsCurrentGoal();
        doBothPreservesTwoActiveGoals();
        switchSuspendsResumableCurrentGoal();
        switchedGoalCompletionResumesSuspendedWork();
        repeatedSwitchKeepsSuspensionBounded();
        askDoesNotMutateTaskState();
        System.out.println("ExecutiveTaskControlTest: " + checks + " assertions passed");
    }

    private static void sameGoalCorrectionReplacesCurrentGoal() {
        ExecutiveTaskController controller = new ExecutiveTaskController("find Chinese food");
        TaskControlResult result = controller.apply(InterruptionDecision.RESTART_CURRENT, "find Italian food instead");
        check(result.action() == InterruptionDecision.RESTART_CURRENT, "restart action");
        check("find Italian food instead".equals(controller.currentGoal()), "correction replaces active goal");
        check(controller.parallelGoal().isBlank(), "restart must not leave stale parallel work");
    }

    private static void contextualRefinementKeepsCurrentGoal() {
        ExecutiveTaskController controller = new ExecutiveTaskController("find dinner");
        controller.apply(InterruptionDecision.INCORPORATE_CONTEXT, "quiet with outdoor seating");
        check("find dinner".equals(controller.currentGoal()), "context refinement keeps active goal identity");
        check(controller.context().contains("quiet with outdoor seating"), "context refinement is retained");
    }

    private static void doBothPreservesTwoActiveGoals() {
        ExecutiveTaskController controller = new ExecutiveTaskController("find dinner");
        controller.apply(InterruptionDecision.DO_BOTH, "check tomorrow's calendar");
        check("find dinner".equals(controller.currentGoal()), "do-both preserves current goal");
        check("check tomorrow's calendar".equals(controller.parallelGoal()), "do-both marks incoming goal as concurrent, not queue-behind");
        check("check tomorrow's calendar".equals(controller.queuedGoal()), "legacy queued accessor remains compatible while semantics are concurrent");
    }

    private static void switchSuspendsResumableCurrentGoal() {
        ExecutiveTaskController controller = new ExecutiveTaskController("compare movies");
        controller.apply(InterruptionDecision.SWITCH, "navigate to the hospital now");
        check("navigate to the hospital now".equals(controller.currentGoal()), "switch activates urgent goal");
        check("compare movies".equals(controller.suspendedGoal()), "switch preserves resumable work instead of silently losing it");
    }

    private static void switchedGoalCompletionResumesSuspendedWork() {
        ExecutiveTaskController controller = new ExecutiveTaskController("compare movies");
        controller.apply(InterruptionDecision.SWITCH, "navigate home now");
        String resumed = controller.completeCurrentGoal();
        check("compare movies".equals(resumed), "completing switched-to goal auto-resumes prior work");
        check("compare movies".equals(controller.currentGoal()), "resumed work becomes active again");
        check(controller.suspendedGoal().isBlank(), "resume consumes suspension slot");
    }

    private static void repeatedSwitchKeepsSuspensionBounded() {
        ExecutiveTaskController controller = new ExecutiveTaskController("task A");
        controller.apply(InterruptionDecision.SWITCH, "task B urgent");
        controller.apply(InterruptionDecision.SWITCH, "task C more urgent");
        check("task B urgent".equals(controller.suspendedGoal()), "single suspension slot is bounded and tracks immediately preempted work");
    }

    private static void askDoesNotMutateTaskState() {
        ExecutiveTaskController controller = new ExecutiveTaskController("send Mom a message");
        TaskControlResult result = controller.apply(InterruptionDecision.ASK, "find dinner");
        check("send Mom a message".equals(controller.currentGoal()), "ask leaves current consequential goal untouched");
        check(result.requiresUserDecision(), "ask explicitly exposes user-decision boundary");
        check(controller.parallelGoal().isBlank(), "ask must not silently start parallel work");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
