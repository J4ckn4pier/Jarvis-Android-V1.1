package com.jarvis.brain;

public record InterruptionContext(
        String currentGoal,
        String incomingUtterance,
        double relatedness,
        boolean sameGoalCorrection,
        boolean currentResumable,
        boolean currentConsequential,
        double incomingUrgency) {
    public InterruptionContext {
        currentGoal = currentGoal == null ? "" : currentGoal.trim();
        incomingUtterance = incomingUtterance == null ? "" : incomingUtterance.trim();
        relatedness = clamp(relatedness);
        incomingUrgency = clamp(incomingUrgency);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
