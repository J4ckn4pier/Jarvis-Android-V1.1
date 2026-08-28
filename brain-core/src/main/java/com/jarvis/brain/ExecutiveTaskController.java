package com.jarvis.brain;

/**
 * Small deterministic state machine that turns interruption classification into
 * explicit executive task-control state. It never executes tools or grants approval.
 * DO_BOTH represents concurrent executive intent; actual worker scheduling belongs
 * to the runtime executor. SWITCH keeps one bounded suspension slot and resumes it
 * automatically when the preempting goal completes.
 */
public final class ExecutiveTaskController {
    private String currentGoal;
    private String parallelGoal = "";
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
                parallelGoal = "";
                suspendedGoal = "";
                context = "";
            }
            case INCORPORATE_CONTEXT -> context = append(context, incoming);
            case DO_BOTH -> parallelGoal = incoming;
            case SWITCH -> {
                suspendedGoal = currentGoal;
                currentGoal = incoming;
                parallelGoal = "";
                context = "";
            }
            case ASK -> {
                return new TaskControlResult(decision, true);
            }
        }
        return new TaskControlResult(decision, false);
    }

    /**
     * Marks the primary goal complete and returns the next primary goal. A SWITCHed
     * task resumes first; otherwise an unfinished parallel goal can become primary.
     */
    public synchronized String completeCurrentGoal() {
        if (!suspendedGoal.isBlank()) {
            currentGoal = suspendedGoal;
            suspendedGoal = "";
            context = "";
            return currentGoal;
        }
        if (!parallelGoal.isBlank()) {
            currentGoal = parallelGoal;
            parallelGoal = "";
            context = "";
            return currentGoal;
        }
        currentGoal = "";
        context = "";
        return currentGoal;
    }

    /** Marks independently running DO_BOTH work complete without disturbing the primary goal. */
    public synchronized String completeParallelGoal() {
        parallelGoal = "";
        return currentGoal;
    }

    public synchronized String currentGoal() { return currentGoal; }
    public synchronized String parallelGoal() { return parallelGoal; }
    /** Backwards-compatible name; the stored goal is eligible for concurrent execution. */
    public synchronized String queuedGoal() { return parallelGoal; }
    public synchronized String suspendedGoal() { return suspendedGoal; }
    public synchronized String context() { return context; }

    private static String append(String existing, String addition) {
        return existing == null || existing.isBlank() ? addition : existing + "\n" + addition;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
