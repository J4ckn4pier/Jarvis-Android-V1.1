package com.jarvis.brain;

/** Drives a bounded ReservationCallAgent dialogue over a provider-independent duplex transport. */
public final class ConversationalCallOrchestrator {
    private final int maxRemoteTurns;

    public ConversationalCallOrchestrator(int maxRemoteTurns) {
        if (maxRemoteTurns < 1) throw new IllegalArgumentException("maxRemoteTurns must be positive");
        this.maxRemoteTurns = maxRemoteTurns;
    }

    public CallOutcome execute(ConversationalCallTransport transport, ConversationalCallRequest request) {
        if (transport == null) return failed("Conversational call transport is not attached.");
        if (request == null) return failed("Conversational call request is missing.");

        ReservationCallAgent agent = new ReservationCallAgent(request.representedUser(), request.preferredTime());
        try (ConversationalCallTransport.Session session = transport.connect(request.destination())) {
            if (session == null) return failed("Conversational call transport returned no session.");

            session.speak(agent.openingLine(request.business()));
            for (int turn = 0; turn < maxRemoteTurns; turn++) {
                String remoteSpeech = session.awaitRemoteSpeech();
                String response = agent.onRemoteSpeech(remoteSpeech);
                if (!response.isBlank()) session.speak(response);

                CallOutcome outcome = agent.outcome();
                if (outcome.status() != CallOutcome.Status.IN_PROGRESS) return outcome;
            }
            return failed("Conversational call turn budget exhausted before a terminal outcome.");
        } catch (Exception error) {
            String detail = error.getMessage() == null || error.getMessage().isBlank()
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
            return failed("Conversational call transport failed: " + detail);
        }
    }

    private static CallOutcome failed(String summary) {
        return new CallOutcome(CallOutcome.Status.FAILED, "", java.util.List.of(), summary);
    }
}
