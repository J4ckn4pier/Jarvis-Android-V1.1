package com.jarvis.brain;

import java.util.ArrayList;
import java.util.List;

public final class PlanExecutor {
    private final ToolRegistry registry;
    private final ApprovalGate approvals;

    public PlanExecutor(ToolRegistry registry, ApprovalGate approvals) {
        this.registry = registry;
        this.approvals = approvals;
    }

    public ExecutionReport execute(Plan plan, ExecutionContext context) {
        List<String> outputs = new ArrayList<>();
        for (PlanStep step : plan.steps()) {
            ToolRegistry.RegisteredTool tool = registry.resolve(step.tool()).orElse(null);
            if (tool == null) return new ExecutionReport(ExecutionReport.Status.FAILED, outputs, step.tool());
            boolean consequential = step.consequential() || tool.spec().consequential();
            if (consequential && !approvals.consume(tool.name())) {
                return new ExecutionReport(ExecutionReport.Status.APPROVAL_REQUIRED, outputs, tool.name());
            }
            ToolResult result = tool.implementation().execute(step.arguments(), context);
            if (result.status() == ToolResult.Status.RETRYABLE_FAILURE) {
                result = tool.implementation().execute(step.arguments(), context);
            }
            outputs.add(result.output());
            if (result.status() != ToolResult.Status.SUCCESS) {
                return new ExecutionReport(ExecutionReport.Status.FAILED, outputs, tool.name());
            }
            context.put("last_tool", tool.name());
            context.put("last_output", result.output());
        }
        return new ExecutionReport(ExecutionReport.Status.COMPLETED, outputs, "");
    }
}
