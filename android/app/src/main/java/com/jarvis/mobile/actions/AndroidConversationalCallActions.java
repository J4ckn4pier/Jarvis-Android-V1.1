package com.jarvis.mobile.actions;

import com.jarvis.brain.CallOutcome;
import com.jarvis.brain.ConversationalCallOrchestrator;
import com.jarvis.brain.ConversationalCallRequest;
import com.jarvis.brain.ConversationalCallTransport;

/** Android-facing adapter for the optional duplex telephony transport. */
public final class AndroidConversationalCallActions {
    private final ConversationalCallTransport transport;

    public AndroidConversationalCallActions(ConversationalCallTransport transport) {
        this.transport = transport;
    }

    public String call(String destination, String business, String representedUser, String preferredTime) {
        if (transport == null) {
            return "Conversational calling is unavailable until a duplex telephony transport is connected.";
        }
        try {
            ConversationalCallRequest request = new ConversationalCallRequest(
                    destination, business, representedUser, preferredTime);
            CallOutcome outcome = new ConversationalCallOrchestrator(8).execute(transport, request);
            return switch (outcome.status()) {
                case CONFIRMED -> "Call confirmed for " + outcome.confirmedTime() + ". " + outcome.summary();
                case ALTERNATIVES_AVAILABLE -> "The call returned alternatives: "
                        + String.join(", ", outcome.alternatives()) + ". " + outcome.summary();
                case FAILED -> "Conversational call failed: " + outcome.summary();
                case IN_PROGRESS -> "Conversational call failed: the call ended without a terminal outcome.";
            };
        } catch (RuntimeException failure) {
            return "Conversational call failed: "
                    + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }
}
