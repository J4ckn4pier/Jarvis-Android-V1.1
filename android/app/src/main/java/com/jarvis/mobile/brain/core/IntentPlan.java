package com.jarvis.mobile.brain.core;

import java.util.Objects;

/** A provider-neutral proposal. It describes intent; it never executes Android code. */
public final class IntentPlan {
    public enum Intent {
        HELP, CALL, DIAL, SMS, EMAIL, CALENDAR, NAVIGATE, OPEN_APP, WEB_SEARCH,
        TIMER, ALARM, FLASHLIGHT_ON, FLASHLIGHT_OFF, VOLUME_UP, VOLUME_DOWN,
        MUTE, UNMUTE, MEDIA_PLAY, MEDIA_PAUSE, MEDIA_NEXT, MEDIA_PREVIOUS,
        ACCESSIBILITY, NOTIFICATIONS, REMEMBER, RECALL, ADD_TASK, LIST_TASKS,
        COMPLETE_TASK, TIME, DATE, BATTERY, GREETING, IDENTITY, THANKS,
        CONVERSATION, KNOWLEDGE_QUERY, UNKNOWN
    }

    private final Intent intent;
    private final String payload;
    private final String canonicalCommand;
    private final String answer;
    private final String cue;
    private final double confidence;

    public IntentPlan(Intent intent, String payload, String canonicalCommand,
                      String answer, String cue, double confidence) {
        this.intent = Objects.requireNonNull(intent, "intent");
        this.payload = clean(payload);
        this.canonicalCommand = clean(canonicalCommand);
        this.answer = clean(answer);
        this.cue = clean(cue);
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    public static IntentPlan unknown() {
        return new IntentPlan(Intent.UNKNOWN, "", "", "", "didnt_understand", 0.0);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    public Intent intent() { return intent; }
    public String payload() { return payload; }
    public String canonicalCommand() { return canonicalCommand; }
    public String answer() { return answer; }
    public String cue() { return cue; }
    public double confidence() { return confidence; }
    public boolean isResolved() { return intent != Intent.UNKNOWN; }
}
