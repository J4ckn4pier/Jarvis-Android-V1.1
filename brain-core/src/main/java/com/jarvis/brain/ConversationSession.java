package com.jarvis.brain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

final class ConversationSession {
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_RECENT_MESSAGES = 12;
    private final Clock clock;
    private final Deque<String> recent = new ArrayDeque<>();
    private Instant activeUntil = Instant.EPOCH;

    ConversationSession(Clock clock) { this.clock = clock; }

    boolean isActive() { return clock.instant().isBefore(activeUntil); }
    void wake() { activeUntil = clock.instant().plus(WINDOW); }
    void touch() { if (isActive()) activeUntil = clock.instant().plus(WINDOW); }
    void sleep() { activeUntil = Instant.EPOCH; }

    void rememberTurn(String text) { remember("USER", text); }
    void rememberAssistantTurn(String text) { remember("JARVIS", text); }

    private void remember(String role, String text) {
        if (text == null || text.isBlank()) return;
        recent.addLast(role + ": " + text.trim());
        while (recent.size() > MAX_RECENT_MESSAGES) recent.removeFirst();
    }

    String snapshot() { return String.join("\n", recent); }
}
