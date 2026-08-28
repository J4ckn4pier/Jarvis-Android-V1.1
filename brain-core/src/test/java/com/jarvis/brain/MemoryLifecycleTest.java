package com.jarvis.brain;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public final class MemoryLifecycleTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        recentRecallProtectsOtherwiseStaleMemory();
        lastAccessTimestampSurvivesEncryptedPersistence();
        System.out.println("MemoryLifecycleTest: " + checks + " assertions passed");
    }

    private static void recentRecallProtectsOtherwiseStaleMemory() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        LongTermMemoryStore store = new LongTermMemoryStore();
        store.put(new RichMemory("episode.blue_car", MemoryType.EPISODE, "Saw a blue car", "observed",
                0.8, 0.10, now.minus(Duration.ofDays(400)), null, Set.of("blue", "car")));
        store.retrieve("blue car", now.minus(Duration.ofDays(5)), 3);
        RichMemory touched = store.history("episode.blue_car").get(0);
        check(touched.lastAccessedAt().equals(now.minus(Duration.ofDays(5))),
                "retrieval should update last-access timestamp");
        int removed = store.prune(new MemoryRetentionPolicy(Duration.ofDays(120), 0.25, 0.80), now);
        check(removed == 0, "recently recalled low-importance memory should be protected from pruning");
    }

    private static void lastAccessTimestampSurvivesEncryptedPersistence() throws Exception {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        LongTermMemoryStore store = new LongTermMemoryStore();
        store.put(new RichMemory("preference.music", MemoryType.PREFERENCE, "Likes instrumental music", "user-stated",
                1.0, 0.70, now.minus(Duration.ofDays(30)), null, Set.of("music")));
        store.retrieve("music", now.minus(Duration.ofDays(2)), 2);
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        var file = Files.createTempDirectory("jarvis-memory-lifecycle").resolve("memory.jrm");
        RichMemoryPersistence.saveEncrypted(store, file, new AesGcmMemoryCipher(key));
        LongTermMemoryStore loaded = RichMemoryPersistence.loadEncrypted(file, new AesGcmMemoryCipher(key));
        check(loaded.history("preference.music").get(0).lastAccessedAt().equals(now.minus(Duration.ofDays(2))),
                "last-access timestamp should survive persistence so retention has durable evidence");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
