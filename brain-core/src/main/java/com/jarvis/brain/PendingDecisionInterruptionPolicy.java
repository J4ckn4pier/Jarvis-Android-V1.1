package com.jarvis.brain;

/**
 * Maps planned work that arrives while an approval/recovery decision is pending onto the
 * explicit interruption vocabulary. This policy never executes or approves anything.
 */
public final class PendingDecisionInterruptionPolicy {
    private final ToolRegistry tools;

    public PendingDecisionInterruptionPolicy(ToolRegistry tools) {
        if (tools == null) throw new IllegalArgumentException("tool registry required");
        this.tools = tools;
    }

    public InterruptionDecision decide(BrainRuntime.Status pendingStatus, String pendingTool, Plan incomingPlan) {
        if (pendingStatus != BrainRuntime.Status.APPROVAL_REQUIRED
                && pendingStatus != BrainRuntime.Status.RECOVERY_REQUIRED) {
            throw new IllegalArgumentException("pending approval or recovery status required");
        }
        if (incomingPlan == null || incomingPlan.steps().isEmpty()) return InterruptionDecision.ASK;

        String blocked = pendingTool == null ? "" : pendingTool.trim();
        for (PlanStep step : incomingPlan.steps()) {
            if (step.consequential()) return InterruptionDecision.ASK;
            ToolRegistry.RegisteredTool tool = tools.resolve(step.tool()).orElse(null);
            if (tool == null || tool.spec().consequential()
                    || tool.spec().executionClass() == ToolExecutionClass.CONSEQUENTIAL) {
                return InterruptionDecision.ASK;
            }
            if (pendingStatus == BrainRuntime.Status.RECOVERY_REQUIRED
                    && !blocked.isBlank() && tool.name().equals(blocked)) {
                return InterruptionDecision.ASK;
            }
        }
        return InterruptionDecision.DO_BOTH;
    }
}
