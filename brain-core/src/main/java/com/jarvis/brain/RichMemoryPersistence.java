package com.jarvis.brain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/** Simple portable persistence for core tests. Android can later back the same semantics with SQLite. */
public final class RichMemoryPersistence {
    private RichMemoryPersistence() {}
    public static void save(LongTermMemoryStore store, Path file) {
        List<String> lines = new ArrayList<>();
        for (RichMemory m : store.snapshotAll()) lines.add(String.join("\t", b64(m.key()), m.type().name(), b64(m.content()), b64(m.source()), Double.toString(m.confidence()), Double.toString(m.importance()), m.validFrom().toString(), m.validUntil() == null ? "" : m.validUntil().toString(), b64(String.join("\u001f", m.tags())), Integer.toString(m.evidenceCount())));
        try { if (file.getParent() != null) Files.createDirectories(file.getParent()); Files.write(file, lines, StandardCharsets.UTF_8); } catch (IOException e) { throw new IllegalStateException("Unable to persist rich memory", e); }
    }
    public static LongTermMemoryStore load(Path file) {
        LongTermMemoryStore store = new LongTermMemoryStore(); if (!Files.exists(file)) return store;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue; String[] p = line.split("\\t", -1); if (p.length != 10) continue;
                Set<String> tags = p[8].isEmpty() ? Set.of() : Set.of(unb64(p[8]).split("\u001f"));
                store.put(new RichMemory(unb64(p[0]), MemoryType.valueOf(p[1]), unb64(p[2]), unb64(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5]), Instant.parse(p[6]), p[7].isEmpty() ? null : Instant.parse(p[7]), tags, Integer.parseInt(p[9])));
            }
            return store;
        } catch (IOException e) { throw new IllegalStateException("Unable to load rich memory", e); }
    }
    private static String b64(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String unb64(String s) { return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8); }
}
