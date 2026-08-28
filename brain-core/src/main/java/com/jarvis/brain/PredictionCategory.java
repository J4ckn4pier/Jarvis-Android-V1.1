package com.jarvis.brain;

public enum PredictionCategory {
    GENERAL(false),
    TIMER(true),
    REMINDER(true),
    CALENDAR_CONFLICT(true),
    IMMINENT_COMMITMENT(true);

    private final boolean timeCriticalSpeechAllowed;

    PredictionCategory(boolean timeCriticalSpeechAllowed) {
        this.timeCriticalSpeechAllowed = timeCriticalSpeechAllowed;
    }

    public boolean timeCriticalSpeechAllowed() { return timeCriticalSpeechAllowed; }
}
