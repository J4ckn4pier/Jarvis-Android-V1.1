package com.jarvis.brain;

import java.util.List;
import java.util.Set;

public final class ToolCapableLocalProviderTest {
    private static int checks;

    public static void main(String[] args) {
        localCortexReceivesTypedToolContractsAndCanReturnPlan();
        localCortexCanReturnNaturalConversation();
        malformedStructuredOutputFailsClosedForRouterFallback();
        System.out.println("ToolCapableLocalProviderTest: " + checks + " assertions passed");
    }

    private static void localCortexReceivesTypedToolContractsAndCanReturnPlan() {
        final String[] body = {""};
        LocalCortexTransport transport = requestBody -> {
            body[0] = requestBody;
            return "{\"choices\":[{\"message\":{\"content\":\"{\\\"mode\\\":\\\"plan\\\",\\\"text\\\":\\\"I'll open it.\\\",\\\"plan\\\":{\\\"goal\\\":\\\"open phone\\\",\\\"steps\\\":[{\\\"tool\\\":\\\"open_dialer\\\",\\\"arguments\\\":{},\\\"consequential\\\":false}]}}\"}}]}";
        };
        LocalOpenAiCompatibleProvider provider = new LocalOpenAiCompatibleProvider("local", "http://127.0.0.1:8080/v1/chat/completions", "qwen", transport);
        ReasoningRequest request = new ReasoningRequest("open my phone app", "[SESSION_TOPIC] phone",
                List.of(new ToolSpec("open_dialer", false, Set.of("phone", "dialer"), Set.of(), "Open phone dialer")));
        ReasoningResult result = provider.reason(request);
        check(result.plan() != null, "structured local cortex response should become a typed Plan");
        check(result.plan().steps().get(0).tool().equals("open_dialer"), "plan should preserve requested registered tool");
        check(result.text().contains("open"), "model-facing response text should survive envelope decode");
        check(body[0].contains("open_dialer") && body[0].contains("Open phone dialer"),
                "local cortex prompt must receive typed tool contracts, not an empty tool list");
        check(body[0].contains("mode") && body[0].contains("plan"), "prompt must explicitly teach structured output contract");
    }

    private static void localCortexCanReturnNaturalConversation() {
        LocalCortexTransport transport = requestBody ->
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"mode\\\":\\\"conversation\\\",\\\"text\\\":\\\"I'm doing well. How are you?\\\"}\"}}]}";
        LocalOpenAiCompatibleProvider provider = new LocalOpenAiCompatibleProvider("local", "http://127.0.0.1:8080/v1/chat/completions", "qwen", transport);
        ReasoningResult result = provider.reason(new ReasoningRequest("how are you", "", List.of()));
        check(result.plan() == null, "ordinary conversation must not fabricate an action plan");
        check(result.text().equals("I'm doing well. How are you?"), "conversation envelope should decode naturally");
    }

    private static void malformedStructuredOutputFailsClosedForRouterFallback() {
        LocalCortexTransport transport = requestBody ->
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"mode\\\":\\\"plan\\\",\\\"text\\\":\\\"doing it\\\",\\\"plan\\\":{bad json}}\"}}]}";
        LocalOpenAiCompatibleProvider provider = new LocalOpenAiCompatibleProvider("local", "http://127.0.0.1:8080/v1/chat/completions", "qwen", transport);
        boolean failed = false;
        try { provider.reason(new ReasoningRequest("do something", "", List.of())); }
        catch (RuntimeException expected) { failed = true; }
        check(failed, "malformed structured plan must fail closed so ProviderRouter can try a safe fallback");
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
