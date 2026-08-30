package com.jarvis.brain;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Avoids reading potentially private context unless the current utterance explicitly makes it relevant. */
public final class KeywordGatedAssistantContextSource implements AssistantContextSource {
    private final AssistantContextSource delegate;
    private final Set<String> triggers;

    public KeywordGatedAssistantContextSource(AssistantContextSource delegate, Set<String> triggers) {
        if (delegate == null) throw new IllegalArgumentException("delegate required");
        this.delegate = delegate;
        Set<String> normalized = new LinkedHashSet<>();
        if (triggers != null) {
            for (String trigger : triggers) {
                if (trigger == null || trigger.isBlank()) continue;
                normalized.add(trigger.toLowerCase(Locale.ROOT).trim());
            }
        }
        this.triggers = Set.copyOf(normalized);
    }

    @Override
    public String contextFor(String utterance) {
        String normalized = utterance == null ? "" : utterance.toLowerCase(Locale.ROOT);
        if (!relevant(normalized)) return "";
        String context = delegate.contextFor(utterance);
        return context == null ? "" : context.trim();
    }

    private boolean relevant(String utterance) {
        if (utterance.isBlank()) return false;
        for (String trigger : triggers) {
            if (utterance.contains(trigger)) return true;
        }
        return false;
    }
}
