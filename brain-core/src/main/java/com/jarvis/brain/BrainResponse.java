package com.jarvis.brain;

public record BrainResponse(Kind kind, String text, Plan plan, boolean sessionActive,
                            boolean acceptedWithoutWakeWord, String contextSnapshot) {
    public enum Kind { CONVERSATION, ACTION_PLAN, REASONING_REQUIRED, IGNORED_AMBIENT }

    public static BrainResponse of(Kind kind, String text, Plan plan, boolean active,
                                   boolean withoutWake, String context) {
        return new BrainResponse(kind, text, plan, active, withoutWake, context == null ? "" : context);
    }
}
