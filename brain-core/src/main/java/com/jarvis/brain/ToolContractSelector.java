package com.jarvis.brain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministically selects a bounded subset of tool contracts for model context.
 * This affects prompt size only; the full ToolRegistry remains authoritative for validation/execution.
 */
public final class ToolContractSelector {
    private ToolContractSelector() {}

    public static List<ToolSpec> select(String utterance, String context, List<ToolSpec> tools, int maxTools) {
        int budget = Math.max(0, maxTools);
        if (budget == 0 || tools == null || tools.isEmpty()) return List.of();

        String utteranceText = normalizePhrase(utterance);
        String combinedText = normalizePhrase((utterance == null ? "" : utterance) + " " + (context == null ? "" : context));
        Set<String> combinedTerms = terms(combinedText);

        ArrayList<Scored> scored = new ArrayList<>();
        boolean anyPositive = false;
        for (ToolSpec tool : tools) {
            int score = score(tool, utteranceText, combinedText, combinedTerms);
            if (score > 0) anyPositive = true;
            scored.add(new Scored(tool, score));
        }

        Comparator<Scored> order = anyPositive
                ? Comparator.comparingInt(Scored::score).reversed().thenComparing(s -> s.tool().name())
                : Comparator.comparing(s -> s.tool().name());
        scored.sort(order);

        ArrayList<ToolSpec> selected = new ArrayList<>();
        for (Scored item : scored) {
            if (selected.size() >= budget) break;
            if (anyPositive && item.score() <= 0 && selected.size() >= Math.min(budget, positiveCount(scored))) break;
            selected.add(item.tool());
        }

        // Fill remaining context budget deterministically so the model retains a small general capability sample.
        if (selected.size() < Math.min(budget, tools.size())) {
            ArrayList<ToolSpec> fallback = new ArrayList<>(tools);
            fallback.sort(Comparator.comparing(ToolSpec::name));
            for (ToolSpec tool : fallback) {
                if (selected.size() >= budget) break;
                if (!selected.contains(tool)) selected.add(tool);
            }
        }
        return List.copyOf(selected);
    }

    private static int score(ToolSpec tool, String utteranceText, String combinedText, Set<String> combinedTerms) {
        if (tool == null) return 0;
        int score = 0;
        String namePhrase = normalizePhrase(tool.name().replace('_', ' '));
        if (!namePhrase.isBlank() && containsPhrase(utteranceText, namePhrase)) score += 8;
        score += 3 * overlapCount(terms(namePhrase), combinedTerms);

        for (String alias : tool.aliases()) {
            String phrase = normalizePhrase(alias);
            if (!phrase.isBlank() && containsPhrase(utteranceText, phrase)) score += 10;
            score += 4 * overlapCount(terms(phrase), combinedTerms);
        }

        for (String required : tool.requiredArguments()) {
            String phrase = normalizePhrase(required.replace('_', ' '));
            if (!phrase.isBlank() && containsPhrase(combinedText, phrase)) score += 7;
            score += 3 * overlapCount(terms(phrase), combinedTerms);
        }

        Set<String> descriptionTerms = terms(tool.description());
        score += overlapCount(descriptionTerms, combinedTerms);
        return score;
    }

    private static int positiveCount(List<Scored> scored) {
        int count = 0;
        for (Scored s : scored) if (s.score() > 0) count++;
        return count;
    }

    private static int overlapCount(Set<String> a, Set<String> b) {
        int hits = 0;
        for (String term : a) if (b.contains(term)) hits++;
        return hits;
    }

    private static boolean containsPhrase(String text, String phrase) {
        if (text == null || phrase == null || text.isBlank() || phrase.isBlank()) return false;
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    private static String normalizePhrase(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static Set<String> terms(String value) {
        Set<String> out = new HashSet<>();
        String normalized = normalizePhrase(value);
        if (normalized.isBlank()) return out;
        Set<String> stop = Set.of("the", "a", "an", "to", "for", "of", "and", "or", "my", "me", "i", "you", "it", "that", "this");
        for (String token : normalized.split(" ")) if (token.length() >= 2 && !stop.contains(token)) out.add(token);
        return out;
    }

    private record Scored(ToolSpec tool, int score) {}
}
