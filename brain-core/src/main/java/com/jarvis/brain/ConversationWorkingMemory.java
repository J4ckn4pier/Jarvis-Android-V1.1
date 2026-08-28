package com.jarvis.brain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Compact structured memory for the open conversation, separate from transcript and durable memory. */
public final class ConversationWorkingMemory {
    private String activeGoal = "";
    private String activeTopic = "";
    private String activePreference = "";
    private String unresolvedQuestion = "";
    private String decision = "";
    private final Map<String,String> entities = new LinkedHashMap<>();

    public synchronized void observeUserTurn(String utterance) {
        if (utterance == null || utterance.isBlank()) return;
        String clean = utterance.trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\b(?:i prefer|i'd prefer|i would prefer|actually i prefer)\\b.*")) activePreference = clean;
        if (lower.matches(".*\\b(?:i'm planning|i am planning|i need to|i need|i want to|i want|i'm trying to|i am trying to|my goal is)\\b.*")) activeGoal = clean;
    }

    public synchronized void apply(SessionStateDelta delta) {
        if (delta == null || delta.isEmpty()) return;
        if (!delta.activeGoal().isBlank()) activeGoal = delta.activeGoal();
        if (!delta.activeTopic().isBlank()) activeTopic = delta.activeTopic();
        if (!delta.preference().isBlank()) activePreference = delta.preference();
        if (!delta.decision().isBlank()) decision = delta.decision();
        if (delta.clearUnresolvedQuestion()) unresolvedQuestion = "";
        if (!delta.unresolvedQuestion().isBlank()) unresolvedQuestion = delta.unresolvedQuestion();
        for (Map.Entry<String,String> entry : delta.entities().entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!key.isBlank() && !value.isBlank()) entities.put(key, value);
        }
    }

    public synchronized String snapshot() {
        StringBuilder out = new StringBuilder();
        append(out, "SESSION_GOAL", activeGoal);
        append(out, "SESSION_TOPIC", activeTopic);
        append(out, "SESSION_PREFERENCE", activePreference);
        for (Map.Entry<String,String> entry : entities.entrySet()) append(out, "SESSION_ENTITY:" + entry.getKey(), entry.getValue());
        append(out, "SESSION_OPEN_QUESTION", unresolvedQuestion);
        append(out, "SESSION_DECISION", decision);
        return out.toString();
    }

    public synchronized void clear() {
        activeGoal = ""; activeTopic = ""; activePreference = ""; unresolvedQuestion = ""; decision = ""; entities.clear();
    }

    private static void append(StringBuilder out, String label, String value) {
        if (value == null || value.isBlank()) return;
        if (out.length() > 0) out.append('\n');
        out.append('[').append(label).append("] ").append(value);
    }
}
