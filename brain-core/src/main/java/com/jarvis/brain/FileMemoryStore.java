package com.jarvis.brain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class FileMemoryStore {
    private final Path file;
    private final Map<String, String> values = new LinkedHashMap<>();

    public FileMemoryStore(Path file) {
        this.file = file;
        load();
    }

    public synchronized void remember(String key, String value) {
        values.put(key, value);
        persist();
    }

    public synchronized Optional<String> recall(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public synchronized List<MemoryRecord> search(String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<MemoryRecord> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).contains(needle) || entry.getValue().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(new MemoryRecord(entry.getKey(), entry.getValue()));
            }
        }
        return List.copyOf(out);
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                values.put(unescape(line.substring(0, tab)), unescape(line.substring(tab + 1)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load memory", e);
        }
    }

    private void persist() {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                lines.add(escape(entry.getKey()) + "\t" + escape(entry.getValue()));
            }
            Files.write(file, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist memory", e);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                out.append(c == 't' ? '\t' : c == 'n' ? '\n' : c);
                escaped = false;
            } else if (c == '\\') escaped = true;
            else out.append(c);
        }
        if (escaped) out.append('\\');
        return out.toString();
    }
}
