package com.jarvis.mobile.brain.providers;

import com.jarvis.mobile.brain.core.IntentPlan;
import org.json.JSONObject;

/** Converts untrusted model JSON to the same finite command vocabulary as local NLU. */
public final class ProviderPlanFactory {
    private ProviderPlanFactory() {}

    public static IntentPlan fromJson(JSONObject json) {
        if (json == null || !"plan".equals(json.optString("kind"))) return IntentPlan.unknown();
        final IntentPlan.Intent intent;
        try { intent = IntentPlan.Intent.valueOf(json.optString("intent", "UNKNOWN")); }
        catch (IllegalArgumentException error) { return IntentPlan.unknown(); }
        if (intent == IntentPlan.Intent.UNKNOWN) return IntentPlan.unknown();
        String payload = json.optString("payload", "").trim();
        String canonical = canonical(intent, payload);
        if (canonical == null) return IntentPlan.unknown();
        return new IntentPlan(intent, payload, canonical, json.optString("answer", ""),
                safeCue(json.optString("cue", "one_moment_sir")), json.optDouble("confidence", .5));
    }

    private static String safeCue(String cue) {
        return cue.matches("[a-z0-9_]{1,64}") ? cue : "one_moment_sir";
    }

    private static String canonical(IntentPlan.Intent i, String p) {
        return switch (i) {
            case HELP -> "help"; case CALL -> required("call ", p); case DIAL -> required("dial ", p);
            case SMS -> required("text ", p); case EMAIL -> required("email ", p);
            case CALENDAR -> required("calendar ", p); case NAVIGATE -> required("navigate ", p);
            case OPEN_APP -> required("open ", p); case WEB_SEARCH -> required("search ", p);
            case TIMER -> required("timer ", p); case ALARM -> required("alarm ", p);
            case FLASHLIGHT_ON -> "flashlight on"; case FLASHLIGHT_OFF -> "flashlight off";
            case VOLUME_UP -> "volume up"; case VOLUME_DOWN -> "volume down"; case MUTE -> "mute";
            case UNMUTE -> "unmute"; case MEDIA_PLAY -> "play"; case MEDIA_PAUSE -> "pause";
            case MEDIA_NEXT -> "next"; case MEDIA_PREVIOUS -> "previous";
            case ACCESSIBILITY -> required("", p); case NOTIFICATIONS -> "notifications";
            case REMEMBER -> required("remember ", p); case RECALL -> p.isEmpty() ? "recall" : "recall " + p;
            case ADD_TASK -> required("add task ", p); case LIST_TASKS -> "tasks";
            case COMPLETE_TASK -> required("complete task ", p); case TIME -> "time"; case DATE -> "date";
            case BATTERY -> "battery"; case KNOWLEDGE_QUERY, GREETING, IDENTITY, THANKS, CONVERSATION -> "";
            case UNKNOWN -> null;
        };
    }
    private static String required(String prefix, String payload) { return payload.isEmpty() ? null : prefix + payload; }
}
