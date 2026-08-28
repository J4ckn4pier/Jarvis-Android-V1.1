package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

public final class SessionTrustBoundaryTest {
    private static int checks;

    public static void main(String[] args) {
        providerIntroducedEntityIsInferredNotConfirmed();
        userStatedEntityMayBecomeConfirmed();
        consequentialPlanCannotHydrateMissingRequiredArgFromInferredSessionEntity();
        System.out.println("SessionTrustBoundaryTest: " + checks + " assertions passed");
    }

    private static void providerIntroducedEntityIsInferredNotConfirmed() {
        ConversationWorkingMemory memory = new ConversationWorkingMemory();
        memory.applyValidated(new SessionStateDelta("", "dinner", Map.of("person", "Sarah"), "", "", "", false),
                "I'm meeting someone for dinner");
        String snapshot = memory.snapshot();
        check(snapshot.contains("SESSION_ENTITY_INFERRED:person"), "provider-introduced entity must be labeled inferred");
        check(!snapshot.contains("SESSION_ENTITY_CONFIRMED:person"), "provider assumption must not become confirmed session truth");
    }

    private static void userStatedEntityMayBecomeConfirmed() {
        ConversationWorkingMemory memory = new ConversationWorkingMemory();
        memory.applyValidated(new SessionStateDelta("", "dinner", Map.of("person", "Sarah", "place", "Castle Cafe"), "", "", "", false),
                "I'm meeting Sarah at Castle Cafe tonight");
        String snapshot = memory.snapshot();
        check(snapshot.contains("SESSION_ENTITY_CONFIRMED:person"), "entity literally supported by user turn may be confirmed");
        check(snapshot.contains("SESSION_ENTITY_CONFIRMED:place"), "user-stated place may be confirmed");
    }

    private static void consequentialPlanCannotHydrateMissingRequiredArgFromInferredSessionEntity() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T03:15:00Z"), ZoneOffset.UTC);
        final int[] calls = {0};
        ReasoningRouter router = request -> {
            calls[0]++;
            if (calls[0] == 1) {
                return new ReasoningResult("local", "Maybe Sarah.", null,
                        new SessionStateDelta("", "messaging", Map.of("recipient", "Sarah"), "", "", "", false));
            }
            return new ReasoningResult("local", "I'll prepare it.",
                    new Plan("message", java.util.List.of(new PlanStep("send_message", Map.of("message", "hello"), true))));
        };
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        core.handle("I need to message someone");
        BrainResponse response = core.handle("send hello");
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                "missing consequential recipient must still clarify instead of silently using inferred session entity");
        check(response.text().toLowerCase().contains("who") || response.text().toLowerCase().contains("recipient"),
                "assistant should explicitly ask for recipient");
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
