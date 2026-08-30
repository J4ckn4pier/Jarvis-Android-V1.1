package com.jarvis.brain;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/** Proves pending outcome follow-ups survive restart without persisting presence/location signals. */
public final class OutcomeFollowupPersistenceTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("jarvis-followup-test");
        Path file = dir.resolve("pending-followups.bin");
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x42);
        MemoryCipher cipher = new AesGcmMemoryCipher(key);
        OutcomeFollowupStore store = new EncryptedFileOutcomeFollowupStore(file, cipher);

        Instant actedAt = Instant.parse("2026-08-29T12:00:00Z");
        RecommendationEpisode episode = new RecommendationEpisode(
                "episode-restart-1", "project", "the private JARVIS migration", actedAt.minusSeconds(30));
        OutcomeFollowupCoordinator first = coordinator(store);
        first.recordActedOn(episode, actedAt);
        check(first.pendingCount() == 1, "acted episode must persist as pending");
        check(Files.exists(file) && Files.size(file) > 0, "durable store must write a payload");

        String ciphertextText = Files.readString(file, StandardCharsets.ISO_8859_1);
        check(!ciphertextText.contains("the private JARVIS migration"),
                "personal episode subject must not be plaintext at rest");
        check(!ciphertextText.toLowerCase().contains("location")
                        && !ciphertextText.toLowerCase().contains("returned_home")
                        && !ciphertextText.toLowerCase().contains("presence"),
                "store must persist episode state only, never raw presence/location trigger data");

        OutcomeFollowupCoordinator afterRestart = coordinator(
                new EncryptedFileOutcomeFollowupStore(file, cipher));
        check(afterRestart.pendingCount() == 1,
                "new coordinator must restore pending acted-on episode after process restart");

        Instant later = actedAt.plus(Duration.ofMinutes(5));
        check(afterRestart.onSignal(episode.id(), FollowupTrigger.USER_RETURNED_HOME, false,
                AttentionController.State.OPEN_IDLE, later).isEmpty(),
                "privacy-rejected presence signal must not consume restored episode");
        OutcomeFollowupCoordinator afterRejectedRestart = coordinator(
                new EncryptedFileOutcomeFollowupStore(file, cipher));
        check(afterRejectedRestart.pendingCount() == 1,
                "privacy-rejected signal must remain durable across another restart");

        check(afterRejectedRestart.onSignal(episode.id(), FollowupTrigger.USER_REOPENED_RELATED_CONTEXT, false,
                AttentionController.State.OPEN_IDLE, later.plusSeconds(1)).isPresent(),
                "safe contextual signal should surface restored follow-up");
        OutcomeFollowupCoordinator afterConsumedRestart = coordinator(
                new EncryptedFileOutcomeFollowupStore(file, cipher));
        check(afterConsumedRestart.pendingCount() == 0,
                "surfaced one-shot follow-up must be durably removed");

        Files.deleteIfExists(file);
        Files.deleteIfExists(dir);
        System.out.println("OutcomeFollowupPersistenceTest passed");
    }

    private static OutcomeFollowupCoordinator coordinator(OutcomeFollowupStore store) {
        return new OutcomeFollowupCoordinator(
                new EpisodeFollowupPolicy(Duration.ZERO),
                new ProactiveExecutive(new AttentionGate(0.70), Duration.ZERO, true),
                store);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
