package com.jarvis.brain;

import java.util.Locale;

/**
 * Compact, structured memory for the currently open conversation. This is intentionally separate
 * from the lossy dialogue transcript and from durable long-term memory.
 */
public final class ConversationWorkingMemory {
    private String activeGoal = "";
    private String activePreference = "";

    public synchronized void observeUserTurn(String utterance) {
        if (utterance == null || utterance.isBlank()) return;
        String clean = utterance.trim();
        String lower = clean.toLowerCase(Locale.ROOT);

        if (lower.matches(".*\\b(?:i prefer|i'd prefer|i would prefer|actually i prefer)\\b.*")) {
            activePreference = clean;
        }
        if (lower.matches(".*\\b(?:i'm planning|i am planning|i need to|i need|i want to|i want|i'm trying to|i am trying to|my goal is)\\b.*")) {
            activeGoal = clean;
        }
    }

    public synchronized String snapshot() {
        StringBuilder out = new StringBuilder();
        if (!activeGoal.isBlank()) out.append("[SESSION_GOAL] ").append(activeGoal);
        if (!activePreference.isBlank()) {
            if (out.length() > 0) out.append('\n');
            out.append("[SESSION_PREFERENCE] ").append(activePreference);
        }
        return out.toString();
    }

    public synchronized void clear() {
        activeGoal = "";
        activePreference = "";
    }
}
