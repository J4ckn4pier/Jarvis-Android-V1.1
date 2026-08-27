package com.jarvis.brain;

import java.util.List;

public final class BrainAdvancedAcceptanceTest {
    private static int passed = 0;

    public static void main(String[] args) {
        providerRouterUsesAvailableReasoningCortexWithContext();
        durableMemorySurvivesReload();
        predictiveAttentionSuppressesNoiseAndSurfacesHighValueEvents();
        reservationDialogueConfirmsPreferredTimeWhenAvailable();
        reservationDialogueCollectsAlternativesWithoutUnauthorizedBooking();
        System.out.println("PASS " + passed + " advanced brain assertions");
    }

    private static void providerRouterUsesAvailableReasoningCortexWithContext() {
        ReasoningProvider unavailable = new ReasoningProvider() {
            public String id() { return "offline"; }
            public boolean available() { return false; }
            public ReasoningResult reason(ReasoningRequest request) { throw new AssertionError("must not call unavailable provider"); }
        };
        ReasoningProvider local = new ReasoningProvider() {
            public String id() { return "local-test"; }
            public boolean available() { return true; }
            public ReasoningResult reason(ReasoningRequest request) { return new ReasoningResult("local-test", "I remember the context: " + request.context(), null); }
        };
        ProviderRouter router = new ProviderRouter(List.of(unavailable, local));
        ReasoningResult result = router.reason(new ReasoningRequest("what should I do next?", "Italian dinner preference", List.of()));
        check(result.providerId().equals("local-test"), "router should choose first available provider");
        check(result.text().contains("Italian dinner preference"), "reasoning request should carry context");
    }

    private static void durableMemorySurvivesReload() {
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("jarvis-memory-test");
            java.nio.file.Path file = dir.resolve("memory.tsv");
            FileMemoryStore first = new FileMemoryStore(file);
            first.remember("food.preference", "Italian");
            FileMemoryStore second = new FileMemoryStore(file);
            check(second.recall("food.preference").orElseThrow().equals("Italian"), "durable memory should survive store reload");
            check(second.search("Italian").stream().anyMatch(m -> m.value().equals("Italian")), "durable memory should be searchable");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static void predictiveAttentionSuppressesNoiseAndSurfacesHighValueEvents() {
        AttentionGate gate = new AttentionGate(0.70);
        PredictionCandidate noise = new PredictionCandidate("maybe mention weather", 0.40, 0.30, 0.20);
        PredictionCandidate important = new PredictionCandidate("leave now for appointment", 0.96, 0.91, 0.90);
        check(!gate.shouldSurface(noise), "low-value prediction should stay subconscious");
        check(gate.shouldSurface(important), "high-confidence urgent prediction should surface");
    }

    private static void reservationDialogueConfirmsPreferredTimeWhenAvailable() {
        ReservationCallAgent agent = new ReservationCallAgent("Charles", "5:00 PM");
        String opening = agent.openingLine("Castle Cafe");
        check(opening.toLowerCase().contains("reservation") && opening.contains("5:00 PM"), "agent should state reservation goal and preferred time");
        String reply = agent.onRemoteSpeech("Yes, we can do five o'clock.");
        check(reply.toLowerCase().contains("confirm"), "agent should confirm requested time when available");
        CallOutcome outcome = agent.outcome();
        check(outcome.status() == CallOutcome.Status.CONFIRMED, "preferred available time should produce confirmed outcome");
        check(outcome.confirmedTime().equals("5:00 PM"), "confirmed outcome should preserve requested time");
    }

    private static void reservationDialogueCollectsAlternativesWithoutUnauthorizedBooking() {
        ReservationCallAgent agent = new ReservationCallAgent("Charles", "5:00 PM");
        agent.openingLine("Castle Cafe");
        String reply = agent.onRemoteSpeech("Five is booked, but I have 5:30 or 6 available.");
        check(reply.toLowerCase().contains("thank"), "agent should politely close after collecting alternatives");
        CallOutcome outcome = agent.outcome();
        check(outcome.status() == CallOutcome.Status.ALTERNATIVES_AVAILABLE, "unavailable preferred time should report alternatives");
        check(outcome.alternatives().contains("5:30 PM") && outcome.alternatives().contains("6:00 PM"), "agent should capture alternative times");
        check(outcome.confirmedTime().isEmpty(), "agent must not book an alternative without authorization");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }
}
