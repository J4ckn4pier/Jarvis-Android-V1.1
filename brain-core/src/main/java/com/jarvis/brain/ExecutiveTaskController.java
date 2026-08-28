package com.jarvis.brain;

/**
 * Small deterministic state machine that turns interruption classification into
 * explicit executive task-control state. It never executes tools or grants approval.
 */
public final class ExecutiveTaskController {
    private String currentGoal;
    private String queuedGoal = "";
    private String suspendedGoal = "";
    private String context = "";

    public ExecutiveTaskController(String currentGoal) {
        this.currentGoal = clean(currentGoal);
        if (this.currentGoal.isBlank()) throw new IllegalArgumentException("current goal required");
    }

    public synchronized TaskControlResult apply(InterruptionDecision decision, String incomingUtterance) {
        if (decision == null) throw new IllegalArgumentException("interruption decision required");
        String incoming = clean(incomingUtterance);
        if (incoming.isBlank()) throw new IllegalArgumentException("incoming utterance required");

        switch (decision) {
            case RESTART_CURRENT -> {
                currentGoal = incoming;
                queuedGoal = "";
                suspendedGoal = "";
                context = "";
            }
            case INCORPORATE_CONTEXT -> context = append(context, incoming);
            case DO_BOTH -> queuedGoal = incoming;
            case SWITCH -> {
                suspendedGoal = currentGoal;
                currentGoal = incoming;
                queuedGoal = "";
                context = "";
            }
            case ASK -> {
                return new TaskControlResult(decision, true);
            }
        }
        return new TaskControlResult(decision, false);
    }

    public synchronized String currentGoal() { return currentGoal; }
    public synchronized String queuedGoal() { return queuedGoal; }
    public synchronized String suspendedGoal() { return suspendedGoal; }
    public synchronized String context() { return context; }

    private static String append(String existing, String addition) {
        return existing == null || existing.isBlank() ? addition : existing + "\n" + addition;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
