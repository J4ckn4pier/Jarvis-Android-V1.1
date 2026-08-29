package com.jarvis.brain;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

/** A supplied duplex transport must turn the shared consequential tool into a real orchestration adapter. */
public final class ConversationalCallToolTransportAdapterTest {
    public static void main(String[] args) {
        ToolRegistry registry = ToolRegistry.standard(ExternalResearchGateway.unavailable(), new FakeTransport("Yes, 5 PM is available."));
        ToolRegistry.RegisteredTool call = registry.resolve("place_conversational_call").orElseThrow();

        ToolResult confirmed = call.implementation().execute(Map.of(
                "business", "Lost Coffee",
                "destination", "+13035550199",
                "represented_user", "Charles",
                "preferred_time", "5 PM"), new ExecutionContext());

        check(confirmed.status() == ToolResult.Status.SUCCESS,
                "attached duplex transport must execute the conversational-call tool");
        check(confirmed.output().contains("status=CONFIRMED"),
                "tool output must expose the confirmed terminal status");
        check(confirmed.output().contains("confirmed_time=5 PM"),
                "tool output must preserve the confirmed time");

        ToolRegistry failing = ToolRegistry.standard(ExternalResearchGateway.unavailable(), FakeTransport.failing("transport offline"));
        ToolResult failure = failing.resolve("place_conversational_call").orElseThrow().implementation().execute(Map.of(
                "business", "Lost Coffee",
                "destination", "+13035550199",
                "represented_user", "Charles",
                "preferred_time", "5 PM"), new ExecutionContext());
        check(failure.status() == ToolResult.Status.FAILURE,
                "transport failure must remain failure at the shared tool boundary");
        check(failure.output().contains("transport offline"),
                "tool failure must preserve actionable transport evidence");

        System.out.println("ConversationalCallToolTransportAdapterTest passed");
    }

    private static final class FakeTransport implements ConversationalCallTransport {
        private final Queue<String> remote = new ArrayDeque<>();
        private final String failure;

        private FakeTransport(String speech) {
            remote.add(speech);
            failure = "";
        }

        private FakeTransport(String failure, boolean ignored) {
            this.failure = failure;
        }

        static FakeTransport failing(String message) {
            return new FakeTransport(message, true);
        }

        @Override public Session connect(String destination) throws Exception {
            if (!failure.isBlank()) throw new Exception(failure);
            return new Session() {
                @Override public void speak(String text) { }
                @Override public String awaitRemoteSpeech() { return remote.isEmpty() ? "" : remote.remove(); }
                @Override public void close() { }
            };
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
