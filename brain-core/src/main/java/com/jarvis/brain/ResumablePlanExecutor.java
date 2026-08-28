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
            if (tool == null) return new ExecutionReport(ExecutionReport.Status.FAILED, cursor.outputs(), step.tool());
            boolean consequential = step.consequential() || tool.spec().consequential();
            if (consequential && !approvals.consume(tool.name())) {
                return new ExecutionReport(ExecutionReport.Status.APPROVAL_REQUIRED, cursor.outputs(), tool.name());
            }
            ToolResult result = tool.implementation().execute(step.arguments(), context);
            if (result.status() == ToolResult.Status.RETRYABLE_FAILURE) {
                result = tool.implementation().execute(step.arguments(), context);
            }
            if (result.status() != ToolResult.Status.SUCCESS) {
                return new ExecutionReport(ExecutionReport.Status.FAILED,
                        append(cursor.outputs(), result.output()), tool.name());
            }
            cursor.advance(result.output());
            context.put("last_tool", tool.name());
            context.put("last_output", result.output());
        }
        return new ExecutionReport(ExecutionReport.Status.COMPLETED, cursor.outputs(), "");
    }

    private static java.util.List<String> append(java.util.List<String> base, String value) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(base);
        out.add(value);
        return java.util.List.copyOf(out);
    }
}
