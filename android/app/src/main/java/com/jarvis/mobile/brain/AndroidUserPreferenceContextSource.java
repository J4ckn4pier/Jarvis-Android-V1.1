package com.jarvis.mobile.brain;

import android.content.Context;
import android.content.SharedPreferences;

import com.jarvis.brain.AssistantContextSource;

/** Supplies explicit user-facing Profile and Personality settings to replaceable reasoning cortexes. */
public final class AndroidUserPreferenceContextSource implements AssistantContextSource {
    private static final String DEFAULT_PROFILE = "Sir";
    private static final String DEFAULT_PERSONALITY = "Humble Butler";

    private final SharedPreferences preferences;

    public AndroidUserPreferenceContextSource(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        preferences = context.getApplicationContext()
                .getSharedPreferences("jarvis_shell", Context.MODE_PRIVATE);
    }

    @Override
    public String contextFor(String utterance) {
        String profile = clean(preferences.getString("profile_name", DEFAULT_PROFILE), DEFAULT_PROFILE);
        String personality = clean(preferences.getString("personality_label", DEFAULT_PERSONALITY), DEFAULT_PERSONALITY);
        return "Explicit user-selected form of address: " + profile + ".\n"
                + "Explicit user-selected personality: " + personality + ".";
    }

    private static String clean(String value, String fallback) {
        String cleaned = value == null ? "" : value.trim().replaceAll("[\\r\\n\\t]+", " ");
        if (cleaned.isEmpty()) return fallback;
        return cleaned.length() > 80 ? cleaned.substring(0, 80).trim() : cleaned;
    }
}
