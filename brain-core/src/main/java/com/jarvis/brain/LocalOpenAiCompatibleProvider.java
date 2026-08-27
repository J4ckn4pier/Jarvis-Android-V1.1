package com.jarvis.brain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalOpenAiCompatibleProvider implements ReasoningProvider {
    private static final Pattern CONTENT = Pattern.compile("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private final String id;
    private final URI endpoint;
    private final String model;
    private final HttpClient client;

    public LocalOpenAiCompatibleProvider(String id, String endpoint, String model) {
        this.id = id;
        this.endpoint = URI.create(endpoint);
        this.model = model;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override public String id() { return id; }
    @Override public boolean available() { return true; }

    @Override
    public ReasoningResult reason(ReasoningRequest request) {
        String system = "You are JARVIS, a concise, capable personal assistant. Maintain conversation context, infer intent, and never respond with an unsupported-command or no-framework message when you can reason, clarify, research, or plan instead.";
        String user = "Context:\n" + request.context() + "\n\nUser:\n" + request.utterance();
        String body = "{\"model\":\"" + escape(model) + "\",\"messages\":[" +
                "{\"role\":\"system\",\"content\":\"" + escape(system) + "\"}," +
                "{\"role\":\"user\",\"content\":\"" + escape(user) + "\"}]," +
                "\"temperature\":0.4}";
        HttpRequest http = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = client.send(http, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new RuntimeException("Local cortex HTTP " + response.statusCode());
            String text = parseContent(response.body());
            return new ReasoningResult(id, text, null);
        } catch (Exception e) {
            throw new RuntimeException("Local reasoning provider failed", e);
        }
    }

    private static String parseContent(String json) {
        Matcher matcher = CONTENT.matcher(json == null ? "" : json);
        if (!matcher.find()) throw new RuntimeException("Local cortex response missing content");
        return unescape(matcher.group(1));
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        boolean slash = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!slash) {
                if (c == '\\') slash = true; else out.append(c);
                continue;
            }
            switch (c) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case '\\' -> out.append('\\');
                case '"' -> out.append('"');
                default -> out.append(c);
            }
            slash = false;
        }
        if (slash) out.append('\\');
        return out.toString();
    }
}
