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
            if (tool == null) return new ExecutionReport(ExecutionReport.Status.FAILED, outputs, step.tool(),
                    "Unknown tool: " + step.tool());
            boolean consequential = step.consequential() || tool.spec().consequential();
            if (consequential && !approvals.consume(tool.name())) {
                return new ExecutionReport(ExecutionReport.Status.APPROVAL_REQUIRED, outputs, tool.name(),
                        "Fresh approval required before consequential execution attempt");
            }

            ToolResult result;
            try {
                result = normalizeToolResult(tool.implementation().execute(step.arguments(), context));
                if (result.status() == ToolResult.Status.RETRYABLE_FAILURE) {
                    if (consequential) {
                        outputs.add(result.output());
                        return new ExecutionReport(ExecutionReport.Status.APPROVAL_REQUIRED, outputs, tool.name(),
                                "Fresh approval required before retrying consequential execution");
                    }
                    result = normalizeToolResult(tool.implementation().execute(step.arguments(), context));
                }
            } catch (RuntimeException failure) {
                String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                outputs.add(detail);
                return new ExecutionReport(ExecutionReport.Status.FAILED, outputs, tool.name(), detail);
            }

            outputs.add(result.output());
            if (result.status() != ToolResult.Status.SUCCESS) {
                return new ExecutionReport(ExecutionReport.Status.FAILED, outputs, tool.name(), result.output());
            }
            context.put("last_tool", tool.name());
            context.put("last_output", result.output());
        }
        return new ExecutionReport(ExecutionReport.Status.COMPLETED, outputs, "", "");
    }

    private static ToolResult normalizeToolResult(ToolResult result) {
        if (result == null) return ToolResult.failure("tool returned no result");
        if (result.status() == ToolResult.Status.SUCCESS && result.output() == null) {
            return ToolResult.failure("tool reported success but returned no output");
        }
        return result;
    }
}