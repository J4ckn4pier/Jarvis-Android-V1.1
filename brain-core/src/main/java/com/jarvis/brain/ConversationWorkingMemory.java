package com.jarvis.brain;

import java.text.Normalizer;
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
    private final Map<String,String> confirmedEntities = new LinkedHashMap<>();
    private final Map<String,String> inferredEntities = new LinkedHashMap<>();

    public synchronized void observeUserTurn(String utterance) {
        if (utterance == null || utterance.isBlank()) return;
        String clean = utterance.trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\b(?:i prefer|i'd prefer|i would prefer|actually i prefer)\\b.*")) activePreference = clean;
        if (lower.matches(".*\\b(?:i'm planning|i am planning|i need to|i need|i want to|i want|i'm trying to|i am trying to|my goal is)\\b.*")) activeGoal = clean;
    }

    /** Backwards-compatible provider-state application. Provider-authored entities are inferred by default. */
    public synchronized void apply(SessionStateDelta delta) {
        applyValidated(delta, "");
    }

    /**
     * Applies cortex-authored state with provenance checks. Entity values are confirmed only when the literal
     * normalized value is supported by the current user turn; otherwise they remain inferred session context.
     */
    public synchronized void applyValidated(SessionStateDelta delta, String userTurn) {
        if (delta == null || delta.isEmpty()) return;
        if (!delta.activeGoal().isBlank()) activeGoal = delta.activeGoal();
        if (!delta.activeTopic().isBlank()) activeTopic = delta.activeTopic();
        if (!delta.preference().isBlank()) activePreference = delta.preference();
        if (!delta.decision().isBlank()) decision = delta.decision();
        if (delta.clearUnresolvedQuestion()) unresolvedQuestion = "";
        if (!delta.unresolvedQuestion().isBlank()) unresolvedQuestion = delta.unresolvedQuestion();

        String normalizedUser = normalize(userTurn);
        for (Map.Entry<String,String> entry : delta.entities().entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (key.isBlank() || value.isBlank()) continue;
            confirmedEntities.remove(key);
            inferredEntities.remove(key);
            String normalizedValue = normalize(value);
            if (!normalizedValue.isBlank() && containsPhrase(normalizedUser, normalizedValue)) confirmedEntities.put(key, value);
            else inferredEntities.put(key, value);
        }
    }

    public synchronized String snapshot() {
        StringBuilder out = new StringBuilder();
        append(out, "SESSION_GOAL", activeGoal);
        append(out, "SESSION_TOPIC", activeTopic);
        append(out, "SESSION_PREFERENCE", activePreference);
        for (Map.Entry<String,String> entry : confirmedEntities.entrySet()) append(out, "SESSION_ENTITY_CONFIRMED:" + entry.getKey(), entry.getValue());
        for (Map.Entry<String,String> entry : inferredEntities.entrySet()) append(out, "SESSION_ENTITY_INFERRED:" + entry.getKey(), entry.getValue());
        append(out, "SESSION_OPEN_QUESTION", unresolvedQuestion);
        append(out, "SESSION_DECISION", decision);
        return out.toString();
    }

    public synchronized void clear() {
        activeGoal = ""; activeTopic = ""; activePreference = ""; unresolvedQuestion = ""; decision = "";
        confirmedEntities.clear(); inferredEntities.clear();
    }

    private static void append(StringBuilder out, String label, String value) {
        if (value == null || value.isBlank()) return;
        if (out.length() > 0) out.append('\n');
        out.append('[').append(label).append("] ").append(value);
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean containsPhrase(String normalizedText, String normalizedPhrase) {
        if (normalizedText.isBlank() || normalizedPhrase.isBlank()) return false;
        return (" " + normalizedText + " ").contains(" " + normalizedPhrase + " ");
    }
}
