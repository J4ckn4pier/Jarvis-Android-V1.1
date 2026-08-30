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
            // Approval tokens are one-shot: one consequential external attempt per token.
            if (consequential && !approvals.consume(tool.name())) {
                return new ExecutionReport(ExecutionReport.Status.APPROVAL_REQUIRED, cursor.outputs(), tool.name(),
                        "Fresh approval required before consequential execution attempt");
            }

            ToolResult result;
            try {
                result = normalizeToolResult(tool.implementation().execute(step.arguments(), context));
                if (result.status() == ToolResult.Status.RETRYABLE_FAILURE) {
                    if (consequential) {
                        return new ExecutionReport(ExecutionReport.Status.APPROVAL_REQUIRED,
                                append(cursor.outputs(), result.output()), tool.name(), result.output());
                    }
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
        if (result == null) return ToolResult.failure("tool returned no result");
        if (result.output() != null && !result.output().isBlank()) return result;
        if (result.status() == ToolResult.Status.SUCCESS) {
            return ToolResult.failure("tool reported success but returned no output");
        }
        if (result.status() == ToolResult.Status.RETRYABLE_FAILURE) {
            return ToolResult.retryableFailure("tool reported retryable failure but returned no output");
        }
        return ToolResult.failure("tool reported failure but returned no output");
    }

    private static java.util.List<String> append(java.util.List<String> base, String value) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(base);
        out.add(value == null ? "" : value);
        return java.util.List.copyOf(out);
    }
}
