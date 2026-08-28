package com.jarvis.brain;

/** Result of applying an interruption decision to executive task state. */
public record TaskControlResult(InterruptionDecision action, boolean requiresUserDecision) {
    public TaskControlResult {
        if (action == null) throw new IllegalArgumentException("task-control action required");
    }
}
