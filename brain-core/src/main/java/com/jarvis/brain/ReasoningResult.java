package com.jarvis.brain;

public record ReasoningResult(String providerId, String text, Plan plan, SessionStateDelta stateDelta) {
    public ReasoningResult {
        stateDelta = stateDelta == null ? SessionStateDelta.empty() : stateDelta;
    }

    public ReasoningResult(String providerId, String text, Plan plan) {
        this(providerId, text, plan, SessionStateDelta.empty());
    }
}
