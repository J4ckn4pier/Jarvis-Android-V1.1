package com.jarvis.brain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portable encrypted durable store for pending outcome follow-ups. Only episode identity/domain/
 * subject/timestamps are persisted; trigger, location, presence, and consent observations are not.
 * Android should supply a Keystore-backed MemoryCipher.
 */
public final class EncryptedFileOutcomeFollowupStore implements OutcomeFollowupStore {
    private final Path file;
    private final MemoryCipher cipher;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public EncryptedFileOutcomeFollowupStore(Path file, MemoryCipher cipher) {
        if (file == null) throw new IllegalArgumentException("file required");
        if (cipher == null) throw new IllegalArgumentException("cipher required");
        this.file = file;
        this.cipher = cipher;
        load();
    }

    @Override
    public synchronized List<Entry> loadAll() {
        return List.copyOf(new ArrayList<>(entries.values()));
    }

    @Override
    public synchronized void upsert(Entry entry) {
        if (entry == null) throw new IllegalArgumentException("entry required");
        entries.put(entry.episode().id(), entry);
        persist();
    }

    @Override
    public synchronized void remove(String episodeId) {
        if (episodeId == null) return;
        if (entries.remove(episodeId.trim()) != null) persist();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            byte[] encrypted = Files.readAllBytes(file);
            if (encrypted.length == 0) return;
            deserialize(cipher.decrypt(encrypted));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load encrypted outcome followups", e);
        }
    }

    private void deserialize(byte[] bytes) {
        String text = new String(bytes == null ? new byte[0] : bytes, StandardCharsets.UTF_8);
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            String[] p = line.split("\\t", -1);
            if (p.length != 5) continue;
            RecommendationEpisode episode = new RecommendationEpisode(
                    unb64(p[0]), unb64(p[1]), unb64(p[2]), Instant.parse(p[3]));
            Entry entry = new Entry(episode, Instant.parse(p[4]));
            entries.put(episode.id(), entry);
        }
    }

    private void persist() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            List<String> lines = new ArrayList<>();
            for (Entry entry : entries.values()) {
                RecommendationEpisode e = entry.episode();
                lines.add(String.join("\t",
                        b64(e.id()), b64(e.domain()), b64(e.subject()),
                        e.recommendedAt().toString(), entry.actedAt().toString()));
            }
            byte[] plaintext = String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
            Files.write(file, cipher.encrypt(plaintext));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist encrypted outcome followups", e);
        }
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
