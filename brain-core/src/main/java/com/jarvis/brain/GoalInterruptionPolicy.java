package com.jarvis.brain;

/**
 * Explicit, legible policy for mid-execution user interruptions.
 * This decides task control only; it does not execute or approve actions.
 */
public final class GoalInterruptionPolicy {
    public InterruptionDecision decide(InterruptionContext context) {
        if (context == null) throw new IllegalArgumentException("interruption context required");

        if (context.sameGoalCorrection()) return InterruptionDecision.RESTART_CURRENT;
        if (context.relatedness() >= 0.65) return InterruptionDecision.INCORPORATE_CONTEXT;

        // High urgency can displace safe resumable work. Consequential work is protected by an ask boundary.
        if (context.incomingUrgency() >= 0.85) {
            return context.currentConsequential() ? InterruptionDecision.ASK : InterruptionDecision.SWITCH;
        }

        if (context.currentConsequential()) return InterruptionDecision.ASK;
        if (context.currentResumable()) return InterruptionDecision.DO_BOTH;

        // Non-resumable but non-consequential work is ambiguous enough to ask rather than silently discard it.
        return InterruptionDecision.ASK;
    }
}
