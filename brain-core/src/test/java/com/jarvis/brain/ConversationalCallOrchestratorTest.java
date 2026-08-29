package com.jarvis.brain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/** Contract for provider-independent duplex conversational-call orchestration. */
public final class ConversationalCallOrchestratorTest {
    public static void main(String[] args) {
        confirmsPreferredReservationOverDuplexTransport();
        returnsAlternativesWithoutPretendingTheyWereBooked();
        transportFailureNeverBecomesSuccess();
        exhaustedConversationFailsTruthfully();
        System.out.println("ConversationalCallOrchestratorTest passed");
    }

    private static void confirmsPreferredReservationOverDuplexTransport() {
        FakeTransport transport = new FakeTransport("Yes, 5 PM is available.");
        ConversationalCallOrchestrator orchestrator = new ConversationalCallOrchestrator(3);

        CallOutcome outcome = orchestrator.execute(
                transport,
                new ConversationalCallRequest("+13035550199", "Lost Coffee", "Charles", "5 PM"));

        check(outcome.status() == CallOutcome.Status.CONFIRMED,
                "confirmed remote speech must produce a confirmed outcome");
        check("5 PM".equals(outcome.confirmedTime()),
                "confirmed outcome must preserve the requested time");
        check(transport.connectedDestination.equals("+13035550199"),
                "orchestrator must connect through the supplied transport destination");
        check(transport.spoken.size() == 2,
                "orchestrator must speak an opening line and the agent confirmation response");
        check(transport.spoken.get(0).contains("calling on behalf of Charles"),
                "opening line must identify the represented user");
        check(transport.spoken.get(1).contains("please confirm the reservation"),
                "agent must speak the confirmation response over the transport");
        check(transport.closed,
                "transport session must always be closed after a terminal outcome");
    }

    private static void returnsAlternativesWithoutPretendingTheyWereBooked() {
        FakeTransport transport = new FakeTransport("We're booked at 5, but 6:30 PM or 7 PM are available.");
        CallOutcome outcome = new ConversationalCallOrchestrator(3).execute(
                transport,
                new ConversationalCallRequest("+13035550199", "Lost Coffee", "Charles", "5 PM"));

        check(outcome.status() == CallOutcome.Status.ALTERNATIVES_AVAILABLE,
                "unavailable preferred time must remain alternatives, not fake confirmation");
        check(outcome.alternatives().contains("6:30 PM"),
                "agent must preserve alternatives heard from the remote party");
        check(outcome.confirmedTime().isBlank(),
                "alternative discovery must not report a confirmed time");
    }

    private static void transportFailureNeverBecomesSuccess() {
        FakeTransport transport = FakeTransport.failingConnect("transport offline");
        CallOutcome outcome = new ConversationalCallOrchestrator(3).execute(
                transport,
                new ConversationalCallRequest("+13035550199", "Lost Coffee", "Charles", "5 PM"));

        check(outcome.status() == CallOutcome.Status.FAILED,
                "connection failure must be represented as FAILED");
        check(outcome.summary().contains("transport offline"),
                "failure summary must retain actionable transport evidence");
        check(transport.spoken.isEmpty(),
                "orchestrator must not pretend it spoke when connection failed");
    }

    private static void exhaustedConversationFailsTruthfully() {
        FakeTransport transport = new FakeTransport(
                "Can you repeat that?",
                "I'm still not sure what you're asking.",
                "Could you call back later?");
        CallOutcome outcome = new ConversationalCallOrchestrator(2).execute(
                transport,
                new ConversationalCallRequest("+13035550199", "Lost Coffee", "Charles", "5 PM"));

        check(outcome.status() == CallOutcome.Status.FAILED,
                "turn-budget exhaustion must fail instead of reporting an in-progress call as success");
        check(outcome.summary().contains("turn budget"),
                "turn-budget failure must be explicit for diagnostics/retry policy");
        check(transport.closed,
                "exhausted sessions must close their transport resources");
    }

    private static final class FakeTransport implements ConversationalCallTransport {
        private final Queue<String> remoteSpeech = new ArrayDeque<>();
        private final String connectFailure;
        private final List<String> spoken = new ArrayList<>();
        private String connectedDestination = "";
        private boolean closed;

        private FakeTransport(String... remoteSpeech) {
            this.connectFailure = "";
            for (String speech : remoteSpeech) this.remoteSpeech.add(speech);
        }

        private FakeTransport(String connectFailure, boolean failing) {
            this.connectFailure = connectFailure;
        }

        static FakeTransport failingConnect(String message) {
            return new FakeTransport(message, true);
        }

        @Override public Session connect(String destination) throws Exception {
            connectedDestination = destination;
            if (!connectFailure.isBlank()) throw new Exception(connectFailure);
            return new Session() {
                @Override public void speak(String text) {
                    spoken.add(text);
                }

                @Override public String awaitRemoteSpeech() {
                    return remoteSpeech.isEmpty() ? "" : remoteSpeech.remove();
                }

                @Override public void close() {
                    closed = true;
                }
            };
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
