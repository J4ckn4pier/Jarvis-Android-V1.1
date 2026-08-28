package com.jarvis.brain;

import java.util.List;

public interface MemoryExtractor {
    List<ExtractedMemory> extractUserStated(String userTurn);
}
