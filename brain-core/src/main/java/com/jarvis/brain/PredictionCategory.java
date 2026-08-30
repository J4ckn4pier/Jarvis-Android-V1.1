package com.jarvis.brain;

public enum PredictionCategory {
    GENERAL(false),
    TIMER(true),
    REMINDER(true),
    CALENDAR_CONFLICT(true),
    IMMINENT_COMMITMENT(true),
    /** Episode-bound callback requested by the product; still subject to proactive-speech opt-in/trust/idle gates. */
    RECOMMENDATION_FOLLOWUP(true);

    private final boolean proactiveSpeechAllowed;

    PredictionCategory(boolean proactiveSpeechAllowed) {
        this.proactiveSpeechAllowed = proactiveSpeechAllowed;
    }

    /** Legacy name retained for compatibility; this is now the category-level proactive speech allow-list. */
    public boolean timeCriticalSpeechAllowed() { return proactiveSpeechAllowed; }
}
