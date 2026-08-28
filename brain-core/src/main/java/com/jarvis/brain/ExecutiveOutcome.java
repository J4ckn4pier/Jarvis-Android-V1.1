package com.jarvis.brain;

public record ExecutiveOutcome(Status status, String text, Plan pendingPlan, int iterations, String context,
                               SessionStateDelta stateDelta) {
    public enum Status { ANSWERED, APPROVAL_REQUIRED, CLARIFICATION_REQUIRED, ITERATION_LIMIT, FAILED }

    public ExecutiveOutcome {
        text = text == null ? "" : text;
        context = context == null ? "" : context;
        iterations = Math.max(0, iterations);
        stateDelta = stateDelta == null ? SessionStateDelta.empty() : stateDelta;
    }

    public ExecutiveOutcome(Status status, String text, Plan pendingPlan, int iterations, String context) {
        this(status, text, pendingPlan, iterations, context, SessionStateDelta.empty());
    }
}
