package com.jarvis.mobile.brain.core;

public final class BrainResult {
    private final IntentPlan plan;
    private final String spokenText;
    private final boolean executed;

    public BrainResult(IntentPlan plan, String spokenText, boolean executed) {
        this.plan = plan == null ? IntentPlan.unknown() : plan;
        this.spokenText = spokenText == null ? "" : spokenText.trim();
        this.executed = executed;
    }

    public IntentPlan plan() { return plan; }
    public String spokenText() { return spokenText; }
    public String cue() { return plan.cue(); }
    public boolean executed() { return executed; }
}
