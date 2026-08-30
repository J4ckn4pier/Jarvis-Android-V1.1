package com.jarvis.brain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** High-precision local extractor for explicit durable statements. */
public final class RuleMemoryExtractor implements MemoryExtractor {
    private static final Pattern PREFER = Pattern.compile("(?i)^i\\s+(?:really\\s+)?prefer\\s+(.+)$");
    private static final Pattern FAVORITE = Pattern.compile("(?i)^my\\s+favorite\\s+(.+?)\\s+is\\s+(.+)$");
    private static final Pattern RELATION_NAME = Pattern.compile("(?i)^my\\s+(mother|mom|father|dad|sister|brother|wife|husband|partner|friend|boss|coworker)'?s?\\s+name\\s+is\\s+(.+)$");
    private static final Pattern MY_FACT = Pattern.compile("(?i)^my\\s+(.+?)\\s+is\\s+(.+)$");

    @Override
    public List<ExtractedMemory> extractUserStated(String userTurn) {
        List<ExtractedMemory> out = new ArrayList<>();
        if (userTurn == null) return out;
        String cleaned = userTurn.trim().replaceFirst("(?i)^actually[, ]+", "");
        for (String sentence : cleaned.split("(?<=[.!?])\\s+")) {
            String s = sentence.trim().replaceAll("[.!?]+$", "").trim();
            s = s.replaceFirst("(?i)^remember\\s+that\\s+", "").trim();
            if (s.isBlank()) continue;
            Matcher m = PREFER.matcher(s);
            if (m.matches()) { String value = m.group(1).trim(); out.add(new ExtractedMemory("preference." + slug(topic(value)), MemoryType.PREFERENCE, "Prefers " + value, 0.86, tags(value))); continue; }
            m = FAVORITE.matcher(s);
            if (m.matches()) { String topic = m.group(1).trim(); String value = m.group(2).trim(); out.add(new ExtractedMemory("preference.favorite_" + slug(topic), MemoryType.PREFERENCE, "Favorite " + topic + " is " + value, 0.92, tags(topic + " " + value))); continue; }
            m = RELATION_NAME.matcher(s);
            if (m.matches()) { String relation = m.group(1).toLowerCase(Locale.ROOT); String name = m.group(2).trim(); out.add(new ExtractedMemory("relationship." + slug(relation) + ".name", MemoryType.RELATIONSHIP, "User's " + relation + " is named " + name, 0.96, tags(relation + " " + name))); continue; }
            m = MY_FACT.matcher(s);
            if (m.matches()) { String subject = m.group(1).trim(); String value = m.group(2).trim(); out.add(new ExtractedMemory("fact.my_" + slug(subject), MemoryType.FACT, "User's " + subject + " is " + value, 0.80, tags(subject + " " + value))); }
        }
        return List.copyOf(out);
    }

    private static String topic(String value) { String[] words = value.split("\\s+"); if (words.length <= 3) return value; return String.join("_", java.util.Arrays.copyOfRange(words, Math.max(0, words.length - 3), words.length)); }
    private static String slug(String v) { return v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", ""); }
    private static Set<String> tags(String v) { Set<String> out = new HashSet<>(); for (String t : v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").split("\\s+")) if (t.length() >= 3) out.add(t); return Set.copyOf(out); }
}
