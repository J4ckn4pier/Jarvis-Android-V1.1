package com.jarvis.brain;

import java.util.Set;

public record ExtractedMemory(String key, MemoryType type, String content, double importance, Set<String> tags) {
    public ExtractedMemory { tags = tags == null ? Set.of() : Set.copyOf(tags); }
}
