package com.jarvis.brain;

import java.time.Clock;

/** Supplies only query-relevant durable memories to the reasoning cortex. */
public final class MemoryContextSource implements AssistantContextSource {
    private final LongTermMemoryStore memory;
    private final Clock clock;
    private final int limit;

    public MemoryContextSource(LongTermMemoryStore memory, Clock clock, int limit) {
        if (memory == null) throw new IllegalArgumentException("memory store required");
        if (clock == null) throw new IllegalArgumentException("clock required");
        this.memory = memory;
        this.clock = clock;
        this.limit = Math.max(0, limit);
    }

    @Override
    public String contextFor(String utterance) {
        if (limit == 0) return "";
        return memory.memoryPack(utterance, clock.instant(), limit);
    }
}
