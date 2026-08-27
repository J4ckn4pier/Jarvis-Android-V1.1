package com.jarvis.mobile.brain.core;

import java.text.Normalizer;
import java.util.Locale;

public final class CommandNormalizer {
    private CommandNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String value = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201C', '"').replace('\u201D', '"')
                .toLowerCase(Locale.ROOT).trim();
        value = value.replaceAll("^[\\s,;:!?.-]+|[\\s,;:!?.-]+$", "");
        value = value.replaceFirst("^(hey\\s+|okay\\s+|ok\\s+)?jarvis\\b[\\s,;:!-]*", "");
        value = value.replaceFirst("^(please\\s+|can you please\\s+|could you please\\s+|would you please\\s+|can you\\s+|could you\\s+|would you\\s+)", "");
        value = value.replaceAll("\\s+", " ").trim();
        return value;
    }
}
