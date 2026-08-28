package com.jarvis.brain;

import java.util.List;

/**
 * Single execution facade between conversational/executive reasoning and platform tools.
 * It auto-runs only plans that do not require unconsumed consequential approval and keeps
 * the exact execution cursor when a consequential boundary is reached.
 */
public final class BrainRuntime {
    public enum Status { COMPLETED, APPROVAL_REQUIRED, RECOVERY_REQUIRED, FAILED, IGNORED }
    public record Result(Status status, String text, String blockedTool, List<String> outputs) {
        public Result {
            if (status == null) throw new IllegalArgumentException("status required");
            text = text == null ? "" : text.trim();
            blockedTool = blockedTool == null ? "" : blockedTool.trim();
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
        }
    }

    private final AssistantCore assistant;
    private final ApprovalGate approvals = new ApprovalGate();
    private final ResumablePlanExecutor executor;
    private ExecutionCursor pending;
    private String pendingTool = "";

    public BrainRuntime(AssistantCore assistant, ToolRegistry tools) {
        if (assistant == null) throw new IllegalArgumentException("assistant required");
        if (tools == null) throw new IllegalArgumentException("tool registry required");
        this.assistant = assistant;
        this.executor = new ResumablePlanExecutor(tools, approvals);
    }

    public synchronized Result handle(String utterance) {
        BrainResponse response = assistant.handle(utterance);
        if (response.kind() == BrainResponse.Kind.IGNORED_AMBIENT) {
            return new Result(Status.IGNORED, "", "", List.of());
        }
        if (response.kind() != BrainResponse.Kind.ACTION_PLAN || response.plan() == null) {
            return new Result(Status.COMPLETED, response.text(), "", List.of());
        }
        pending = executor.start(response.plan());
        return runPending(response.text());
    }

    public synchronized Result approvePending() {
        if (pending == null || pendingTool.isBlank()) {
            return new Result(Status.FAILED, "There is no action waiting for approval.", "", List.of());
        }
        approvals.approve(pendingTool);
        return runPending("");
    }

    public synchronized void cancelPending() {
        pending = null;
        pendingTool = "";
    }

    public synchronized boolean hasPendingApproval() {
        return pending != null && !pendingTool.isBlank();
    }

    private Result runPending(String assistantText) {
        ExecutionReport report = executor.run(pending, new ExecutionContext());
        return switch (report.status()) {
            case COMPLETED -> {
                String text = lastNonBlank(report.outputs(), assistantText);
                pending = null;
                pendingTool = "";
                yield new Result(Status.COMPLETED, text, "", report.outputs());
            }
            case APPROVAL_REQUIRED -> {
                pendingTool = report.blockedTool();
                String text = assistantText == null || assistantText.isBlank()
                        ? "I need your approval before I do that."
                        : assistantText;
                yield new Result(Status.APPROVAL_REQUIRED, text, pendingTool, report.outputs());
            }
            case RECOVERY_REQUIRED -> {
                pendingTool = report.blockedTool();
                yield new Result(Status.RECOVERY_REQUIRED,
                        report.failureDetail().isBlank() ? "That action needs recovery before I retry it." : report.failureDetail(),
                        pendingTool, report.outputs());
            }
            case FAILED -> {
                String detail = report.failureDetail().isBlank() ? "That action failed safely." : report.failureDetail();
                pending = null;
                pendingTool = "";
                yield new Result(Status.FAILED, detail, report.blockedTool(), report.outputs());
            }
        };
    }

    private static String lastNonBlank(List<String> outputs, String fallback) {
        if (outputs != null) {
            for (int i = outputs.size() - 1; i >= 0; i--) {
                String value = outputs.get(i);
                if (value != null && !value.isBlank()) return value.trim();
            }
        }
        return fallback == null ? "" : fallback.trim();
    }
}
