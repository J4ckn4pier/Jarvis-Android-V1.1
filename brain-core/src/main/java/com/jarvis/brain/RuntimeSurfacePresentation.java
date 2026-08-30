package com.jarvis.brain;

/** Platform-neutral projection of BrainRuntime outcomes for voice, overlay, and full-app surfaces. */
public record RuntimeSurfacePresentation(
        AssistantSurfaceState state,
        String text,
        String detail,
        RuntimeSurfaceAction primaryAction,
        RuntimeSurfaceAction secondaryAction) {

    public RuntimeSurfacePresentation {
        if (state == null) throw new IllegalArgumentException("state required");
        text = text == null ? "" : text.trim();
        detail = detail == null ? "" : detail.trim();
        primaryAction = primaryAction == null ? RuntimeSurfaceAction.NONE : primaryAction;
        secondaryAction = secondaryAction == null ? RuntimeSurfaceAction.NONE : secondaryAction;
    }

    public static RuntimeSurfacePresentation from(BrainRuntime.Result result) {
        if (result == null) throw new IllegalArgumentException("runtime result required");
        return switch (result.status()) {
            case COMPLETED -> new RuntimeSurfacePresentation(
                    AssistantSurfaceState.ACTION_DONE, result.text(), "",
                    RuntimeSurfaceAction.NONE, RuntimeSurfaceAction.NONE);
            case APPROVAL_REQUIRED -> new RuntimeSurfacePresentation(
                    AssistantSurfaceState.AWAITING_APPROVAL, result.text(),
                    result.blockedTool().isBlank() ? "Approval required" : "Pending: " + result.blockedTool(),
                    RuntimeSurfaceAction.APPROVE, RuntimeSurfaceAction.CANCEL);
            case RECOVERY_REQUIRED -> new RuntimeSurfacePresentation(
                    AssistantSurfaceState.NEEDS_INPUT, result.text(),
                    result.blockedTool().isBlank() ? "Recovery required" : "Blocked: " + result.blockedTool(),
                    RuntimeSurfaceAction.RETRY, RuntimeSurfaceAction.CANCEL);
            case FAILED -> new RuntimeSurfacePresentation(
                    AssistantSurfaceState.ERROR, result.text(), result.blockedTool(),
                    RuntimeSurfaceAction.NONE, RuntimeSurfaceAction.NONE);
            case IGNORED -> new RuntimeSurfacePresentation(
                    AssistantSurfaceState.IDLE, "", "",
                    RuntimeSurfaceAction.NONE, RuntimeSurfaceAction.NONE);
        };
    }
}
