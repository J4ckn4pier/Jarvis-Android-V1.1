package com.jarvis.brain;

/** Projects canonical attention state into UI states; ACTION_DONE is an explicit runtime outcome. */
public final class AssistantSurfaceController {
    private final AttentionController attention;
    private AssistantSurfaceState override;
    private String detail = "";

    public AssistantSurfaceController(AttentionController attention) {
        if (attention == null) throw new IllegalArgumentException("attention controller required");
        this.attention = attention;
    }

    public synchronized AssistantSurfaceState state() {
        if (override != null) return override;
        return switch (attention.state()) {
            case SLEEPING, OPEN_IDLE -> AssistantSurfaceState.IDLE;
            case LISTENING -> AssistantSurfaceState.LISTENING;
            case THINKING -> AssistantSurfaceState.THINKING;
            case SPEAKING -> AssistantSurfaceState.RESPONDING;
        };
    }

    public synchronized String detail() { return detail; }

    public synchronized void markActionDone(String outcome) {
        override = AssistantSurfaceState.ACTION_DONE;
        detail = outcome == null ? "" : outcome.trim();
    }

    public synchronized void clearOutcome() {
        override = null;
        detail = "";
    }
}
