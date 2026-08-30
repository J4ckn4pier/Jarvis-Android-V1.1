package com.jarvis.brain;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class BrainProviderAcceptanceTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        assistantCoreTurnsReasoningFallbackIntoNaturalProviderAnswer();
        localOpenAiCompatibleProviderSendsContextAndReadsAnswer();
        providerFailureFallsThroughToNextCortex();
        System.out.println("PASS " + passed + " provider assertions");
    }

    private static void assistantCoreTurnsReasoningFallbackIntoNaturalProviderAnswer() {
        BrainEngine reflex = BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC));
        ReasoningProvider provider = new ReasoningProvider() {
            public String id() { return "local"; }
            public boolean available() { return true; }
            public ReasoningResult reason(ReasoningRequest request) {
                return new ReasoningResult("local", "Tomorrow looks busy. I'd start by grouping the errands and protecting your morning focus block.", null);
            }
        };
        AssistantCore core = new AssistantCore(reflex, new ProviderRouter(List.of(provider)));
        core.handle("Hey Jarvis");
        BrainResponse response = core.handle("figure out the best way to make tomorrow less chaotic");
        check(response.kind() == BrainResponse.Kind.CONVERSATION, "reasoning provider answer should become normal conversation");
        check(response.text().contains("Tomorrow looks busy"), "provider answer should be returned to user");
    }

    private static void localOpenAiCompatibleProviderSendsContextAndReadsAnswer() throws Exception {
        final String[] requestBody = {""};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody[0] = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"I'm good, and I'm keeping track of our dinner plan.\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            LocalOpenAiCompatibleProvider provider = new LocalOpenAiCompatibleProvider("local-http", "http://127.0.0.1:" + port + "/v1/chat/completions", "jarvis-local");
            ReasoningResult result = provider.reason(new ReasoningRequest("How are you?", "Italian dinner preference", List.of()));
            check(result.text().contains("keeping track"), "local OpenAI-compatible provider should parse assistant content");
            check(requestBody[0].contains("Italian dinner preference"), "provider request must include context");
            check(requestBody[0].contains("How are you?"), "provider request must include user utterance");
        } finally {
            server.stop(0);
        }
    }

    private static void providerFailureFallsThroughToNextCortex() {
        ReasoningProvider broken = new ReasoningProvider() {
            public String id() { return "broken"; }
            public boolean available() { return true; }
            public ReasoningResult reason(ReasoningRequest request) { throw new RuntimeException("boom"); }
        };
        ReasoningProvider backup = new ReasoningProvider() {
            public String id() { return "backup"; }
            public boolean available() { return true; }
            public ReasoningResult reason(ReasoningRequest request) { return new ReasoningResult("backup", "Recovered.", null); }
        };
        ReasoningResult result = new ProviderRouter(List.of(broken, backup)).reason(new ReasoningRequest("hello", "", List.of()));
        check(result.providerId().equals("backup"), "provider router should recover by trying next cortex");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }
}
