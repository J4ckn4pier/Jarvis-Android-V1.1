package com.jarvis.brain;

public final class ResumablePlanExecutor {
    private final ToolRegistry registry;
    private final ApprovalGate approvals;

    public ResumablePlanExecutor(ToolRegistry registry, ApprovalGate approvals) {
        this.registry = registry;
        this.approvals = approvals;
    }

    public ExecutionCursor start(Plan plan) { return new ExecutionCursor(plan); }

    public ExecutionReport run(ExecutionCursor cursor, ExecutionContext context) {
        while (cursor.nextStepIndex() < cursor.plan().steps().size()) {
            PlanStep step = cursor.plan().steps().get(cursor.nextStepIndex());
            ToolRegistry.RegisteredTool tool = registry.resolve(step.tool()).orElse(null);
            if (tool == null) {
                return new ExecutionReport(ExecutionReport.Status.FAILED, cursor.outputs(), step.tool(),
                        "Unknown tool: " + step.tool());
            }

            boolean consequential = step.consequential() || tool.spec().consequential();
            // Approval tokens are one-shot. Any later resume/retry must obtain a fresh token before another attempt.
            if (consequential && !approvals.consume(tool.name())) {
                return new ExecutionReport(ExecutionReport.Status.APPROVAL_REQUIRED, cursor.outputs(), tool.name(),
                        "Fresh approval required before consequential execution attempt");
            }

            ToolResult result;
            try {
                result = normalizeToolResult(tool.implementation().execute(step.arguments(), context));
                if (result.status() == ToolResult.Status.RETRYABLE_FAILURE) {
                    result = normalizeToolResult(tool.implementation().execute(step.arguments(), context));
                    if (result.status() == ToolResult.Status.RETRYABLE_FAILURE) {
                        return new ExecutionReport(ExecutionReport.Status.RECOVERY_REQUIRED,
                                append(cursor.outputs(), result.output()), tool.name(), result.output());
                    }
                }
            } catch (RuntimeException failure) {
                String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                return new ExecutionReport(ExecutionReport.Status.FAILED,
                        append(cursor.outputs(), detail), tool.name(), detail);
            }

            if (result.status() != ToolResult.Status.SUCCESS) {
                return new ExecutionReport(ExecutionReport.Status.FAILED,
                        append(cursor.outputs(), result.output()), tool.name(), result.output());
            }

            cursor.advance(result.output());
            context.put("last_tool", tool.name());
            context.put("last_output", result.output());
        }
        return new ExecutionReport(ExecutionReport.Status.COMPLETED, cursor.outputs(), "", "");
    }

    private static ToolResult normalizeToolResult(ToolResult result) {
        return result == null ? ToolResult.failure("tool returned no result") : result;
    }

    private static java.util.List<String> append(java.util.List<String> base, String value) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(base);
        out.add(value == null ? "" : value);
        return java.util.List.copyOf(out);
    }
}