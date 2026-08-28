package com.jarvis.brain;

import java.time.Duration;
import java.time.Instant;

/** Conservative pruning policy: only stale, low-importance memories are eligible. */
public final class MemoryRetentionPolicy {
    private final Duration staleAfter;
    private final double pruneBelowImportance;
    private final double protectedImportance;

    public MemoryRetentionPolicy(Duration staleAfter, double pruneBelowImportance, double protectedImportance) {
        this.staleAfter = staleAfter == null || staleAfter.isNegative() ? Duration.ZERO : staleAfter;
        this.pruneBelowImportance = clamp(pruneBelowImportance);
        this.protectedImportance = clamp(protectedImportance);
    }

    public boolean shouldPrune(RichMemory memory, Instant now) {
        if (memory == null || now == null) return false;
        if (memory.importance() >= protectedImportance) return false;
        if (memory.importance() > pruneBelowImportance) return false;
        Duration age = Duration.between(memory.validFrom(), now);
        return !age.isNegative() && age.compareTo(staleAfter) >= 0;
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
