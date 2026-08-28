package com.jarvis.brain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalOpenAiCompatibleProvider implements ReasoningProvider {
    private static final Pattern CONTENT = Pattern.compile("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private final String id;
    private final String model;
    private final LocalCortexTransport transport;

    public LocalOpenAiCompatibleProvider(String id, String endpoint, String model) {
        this(id, endpoint, model, httpTransport(endpoint));
    }

    public LocalOpenAiCompatibleProvider(String id, String endpoint, String model, LocalCortexTransport transport) {
        this.id = id == null || id.isBlank() ? "local" : id;
        URI.create(endpoint);
        this.model = model == null ? "" : model;
        if (transport == null) throw new IllegalArgumentException("local cortex transport required");
        this.transport = transport;
    }

    @Override public String id() { return id; }
    @Override public boolean available() { return true; }

    @Override
    public ReasoningResult reason(ReasoningRequest request) {
        String system = buildSystemPrompt(request.tools());
        String user = "Context:\n" + nullToEmpty(request.context()) + "\n\nUser:\n" + nullToEmpty(request.utterance());
        String body = "{\"model\":\"" + escape(model) + "\",\"messages\":[" +
                "{\"role\":\"system\",\"content\":\"" + escape(system) + "\"}," +
                "{\"role\":\"user\",\"content\":\"" + escape(user) + "\"}]," +
                "\"temperature\":0.35}";
        try {
            String outer = transport.send(body);
            String content = parseContent(outer).trim();
            if (!content.startsWith("{")) return new ReasoningResult(id, content, null);
            return decodeEnvelope(content);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Local reasoning provider failed", e);
        }
    }

    private ReasoningResult decodeEnvelope(String json) {
        String mode = stringField(json, "mode");
        String text = stringField(json, "text");
        if (mode.equals("conversation")) return new ReasoningResult(id, text, null);
        if (!mode.equals("plan")) throw new RuntimeException("Local cortex structured response has unsupported mode: " + mode);
        String planJson = objectField(json, "plan");
        try {
            return new ReasoningResult(id, text, PlanJsonCodec.decode(planJson));
        } catch (RuntimeException malformed) {
            throw new RuntimeException("Local cortex returned malformed plan", malformed);
        }
    }

    private static String buildSystemPrompt(List<ToolSpec> tools) {
        StringBuilder out = new StringBuilder();
        out.append("You are JARVIS, a capable personal AI assistant. Be calm, concise, natural, slightly formal, warm but restrained, with dry wit only when appropriate. ")
                .append("Maintain context and infer the user's actual goal. Never answer with 'no framework', 'unsupported command', or equivalent when you can converse, clarify, research, or plan. ")
                .append("Return exactly one JSON object. For ordinary conversation: {\"mode\":\"conversation\",\"text\":\"your reply\"}. ")
                .append("For an action/tool plan: {\"mode\":\"plan\",\"text\":\"brief user-facing reply\",\"plan\":{\"goal\":\"goal\",\"steps\":[{\"tool\":\"registered_tool\",\"arguments\":{},\"consequential\":false}]}}. ")
                .append("Never invent a tool. Do not omit required arguments just to avoid clarifying. Consequential status is enforced again by JARVIS, so do not try to weaken it.\n\nRegistered tools:\n");
        if (tools == null || tools.isEmpty()) out.append("(none; converse or clarify only)\n");
        else for (ToolSpec tool : tools) out.append("- ").append(tool.name())
                .append(" | aliases=").append(tool.aliases())
                .append(" | required=").append(tool.requiredArguments())
                .append(" | consequential=").append(tool.consequential())
                .append(" | description=").append(tool.description()).append('\n');
        return out.toString();
    }

    private static LocalCortexTransport httpTransport(String endpoint) {
        URI uri = URI.create(endpoint);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        return body -> {
            HttpRequest http = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            try {
                HttpResponse<String> response = client.send(http, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) throw new RuntimeException("Local cortex HTTP " + response.statusCode());
                return response.body();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Local reasoning provider interrupted", interrupted);
            } catch (Exception e) {
                throw new RuntimeException("Local reasoning provider failed", e);
            }
        };
    }

    private static String parseContent(String json) {
        Matcher matcher = CONTENT.matcher(json == null ? "" : json);
        if (!matcher.find()) throw new RuntimeException("Local cortex response missing content");
        return unescape(matcher.group(1));
    }

    private static String stringField(String json, String key) {
        int colon = findFieldColon(json, key);
        int quote = skipWhitespaceTo(json, colon + 1);
        if (quote < 0 || json.charAt(quote) != '"') throw new RuntimeException("Missing string field: " + key);
        return parseJsonString(json, quote).value();
    }

    private static String objectField(String json, String key) {
        int colon = findFieldColon(json, key);
        int start = skipWhitespaceTo(json, colon + 1);
        if (start < 0 || json.charAt(start) != '{') throw new RuntimeException("Missing object field: " + key);
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return json.substring(start, i + 1);
        }
        throw new RuntimeException("Unterminated object field: " + key);
    }

    private static int findFieldColon(String json, String key) {
        Pattern field = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:");
        Matcher matcher = field.matcher(json == null ? "" : json);
        if (!matcher.find()) throw new RuntimeException("Missing field: " + key);
        return matcher.end() - 1;
    }

    private static int skipWhitespaceTo(String json, int start) {
        for (int i = Math.max(0, start); i < json.length(); i++) if (!Character.isWhitespace(json.charAt(i))) return i;
        return -1;
    }

    private record ParsedString(String value, int end) {}
    private static ParsedString parseJsonString(String json, int openingQuote) {
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = openingQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (!escaped) {
                if (c == '"') return new ParsedString(out.toString(), i + 1);
                if (c == '\\') escaped = true; else out.append(c);
            } else {
                switch (c) {
                    case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                    case '"' -> out.append('"'); case '\\' -> out.append('\\'); case '/' -> out.append('/');
                    default -> out.append(c);
                }
                escaped = false;
            }
        }
        throw new RuntimeException("Unterminated JSON string");
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        boolean slash = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!slash) { if (c == '\\') slash = true; else out.append(c); continue; }
            switch (c) {
                case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                case '\\' -> out.append('\\'); case '"' -> out.append('"'); default -> out.append(c);
            }
            slash = false;
        }
        if (slash) out.append('\\');
        return out.toString();
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }
}
