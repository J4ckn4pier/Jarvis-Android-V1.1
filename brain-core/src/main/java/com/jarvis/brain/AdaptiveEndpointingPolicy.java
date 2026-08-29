package com.jarvis.brain;

import java.util.Locale;
import java.util.Set;

/**
 * Provider-neutral speech endpointing hints. These values are deliberately conservative:
 * Android recognizers may treat silence extras as hints, but the policy keeps JARVIS from
 * configuring hair-trigger cutoffs and extends the final-pause window around hesitations.
 */
public final class AdaptiveEndpointingPolicy {
    private static final Set<String> HESITATIONS = Set.of("uh", "um", "erm", "hmm", "like", "so", "well", "and", "but", "because");

    public long completeSilenceMillis(String partialTranscript) {
        String normalized = normalize(partialTranscript);
        if (normalized.isEmpty()) return 2400L;
        String last = lastWord(normalized);
        if (HESITATIONS.contains(last) || normalized.endsWith("...") || normalized.endsWith(",")) return 3200L;
        if (isDecisiveShortReply(normalized)) return 1400L;
        return 2100L;
    }

    public long possiblyCompleteSilenceMillis(String partialTranscript) {
        long complete = completeSilenceMillis(partialTranscript);
        return Math.max(900L, complete - 800L);
    }

    public long minimumUtteranceMillis() { return 800L; }

    private static boolean isDecisiveShortReply(String value) {
        return value.equals("yes") || value.equals("no") || value.equals("cancel") || value.equals("later")
                || value.equals("retry") || value.equals("stop") || value.equals("send it") || value.equals("go ahead");
    }

    private static String lastWord(String value) {
        String cleaned = value.replaceAll("[.!?]+$", "").trim();
        int index = cleaned.lastIndexOf(' ');
        return index < 0 ? cleaned : cleaned.substring(index + 1);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
