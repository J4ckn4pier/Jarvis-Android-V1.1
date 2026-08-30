package com.jarvis.brain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

public final class ProviderRecoveryTest {
    private static int checks;

    public static void main(String[] args) {
        failedLocalCortexIsRetriedAfterCooldown();
        System.out.println("ProviderRecoveryTest: " + checks + " assertions passed");
    }

    private static void failedLocalCortexIsRetriedAfterCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-28T02:40:00Z"));
        MutableProvider local = new MutableProvider("local", true, "local recovered");
        MutableProvider fallback = new MutableProvider("fallback", false, "fallback ok");
        PolicyProviderRouter router = new PolicyProviderRouter(List.of(
                new ProviderRoute(local, ProviderTier.FREE_LOCAL, 1),
                new ProviderRoute(fallback, ProviderTier.FREE_LOCAL, 2)
        ), false, 2, Duration.ofMinutes(5), clock);

        ReasoningRequest request = new ReasoningRequest("help", "", List.of());
        check(router.reason(request).providerId().equals("fallback"), "fallback should serve first local failure");
        check(router.reason(request).providerId().equals("fallback"), "fallback should serve second local failure");
        check(router.reason(request).providerId().equals("fallback"), "open circuit should skip broken local before cooldown");
        check(local.calls == 2, "local should be suppressed once failure threshold opens circuit");

        local.fail = false;
        clock.advance(Duration.ofMinutes(6));
        ReasoningResult recovered = router.reason(request);
        check(recovered.providerId().equals("local"), "local-first cortex should be probed again after cooldown");
        check(local.calls == 3, "cooldown expiry should permit one recovery probe");
        check(router.failureCount("local") == 0, "successful recovery should close/reset circuit failure count");
    }

    private static final class MutableProvider implements ReasoningProvider {
        private final String id;
        private boolean fail;
        private final String text;
        private int calls;
        MutableProvider(String id, boolean fail, String text) { this.id=id; this.fail=fail; this.text=text; }
        public String id() { return id; }
        public boolean available() { return true; }
        public ReasoningResult reason(ReasoningRequest request) {
            calls++;
            if (fail) throw new RuntimeException("temporary failure");
            return new ReasoningResult(id, text, null);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
