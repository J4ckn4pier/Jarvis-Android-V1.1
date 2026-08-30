package com.jarvis.brain;

import java.util.ArrayList;
import java.util.List;

/** Combines independently-owned context sources without forcing providers to understand their storage. */
public final class CompositeAssistantContextSource implements AssistantContextSource {
    private final List<AssistantContextSource> sources;

    public CompositeAssistantContextSource(List<AssistantContextSource> sources) {
        List<AssistantContextSource> safe = new ArrayList<>();
        if (sources != null) {
            for (AssistantContextSource source : sources) {
                if (source != null) safe.add(source);
            }
        }
        this.sources = List.copyOf(safe);
    }

    @Override
    public String contextFor(String utterance) {
        StringBuilder combined = new StringBuilder();
        for (AssistantContextSource source : sources) {
            String section = source.contextFor(utterance);
            if (section == null || section.isBlank()) continue;
            if (combined.length() > 0) combined.append("\n\n");
            combined.append(section.trim());
        }
        return combined.toString();
    }
}
