package com.jarvis.brain;

import java.util.List;

public final class PolicyProviderRouterTest {
    private static int checks = 0;

    public static void main(String[] args) {
        freeLocalWinsOverPaid();
        paidIsBlockedWithoutOptIn();
        repeatedFailuresOpenCircuit();
        availabilityProbeFailureFallsThroughToNextProvider();
        System.out.println("PolicyProviderRouterTest: " + checks + " assertions passed");
    }

    private static void freeLocalWinsOverPaid() {
        StubProvider paid = new StubProvider("paid", new ReasoningResult("paid", "paid", null), false);
        StubProvider local = new StubProvider("local", new ReasoningResult("local", "local", null), false);
        PolicyProviderRouter router = new PolicyProviderRouter(List.of(
                new ProviderRoute(paid, ProviderTier.PAID_EXTERNAL, 10),
                new ProviderRoute(local, ProviderTier.FREE_LOCAL, 1)
        ), false, 2);
        ReasoningResult out = router.reason(new ReasoningRequest("hi", "", List.of()));
        check("local".equals(out.providerId()), "free local provider should win regardless of input order");
        check(paid.calls == 0, "paid provider should not be touched when free succeeds");
    }

    private static void paidIsBlockedWithoutOptIn() {
        StubProvider paid = new StubProvider("paid", new ReasoningResult("paid", "paid", null), false);
        PolicyProviderRouter router = new PolicyProviderRouter(
                List.of(new ProviderRoute(paid, ProviderTier.PAID_EXTERNAL, 1)), false, 2);
        ReasoningResult out = router.reason(new ReasoningRequest("hi", "", List.of()));
        check("none".equals(out.providerId()), "paid-only route should return none without opt-in");
        check(paid.calls == 0, "paid provider must never run without opt-in");
    }

    private static void repeatedFailuresOpenCircuit() {
        StubProvider broken = new StubProvider("broken", null, true);
        StubProvider fallback = new StubProvider("fallback", new ReasoningResult("fallback", "ok", null), false);
        PolicyProviderRouter router = new PolicyProviderRouter(List.of(
                new ProviderRoute(broken, ProviderTier.FREE_LOCAL, 1),
                new ProviderRoute(fallback, ProviderTier.FREE_LOCAL, 2)
        ), false, 2);
        router.reason(new ReasoningRequest("a", "", List.of()));
        router.reason(new ReasoningRequest("b", "", List.of()));
        router.reason(new ReasoningRequest("c", "", List.of()));
        check(broken.calls == 2, "provider should be circuit-broken after configured consecutive failures");
        check(fallback.calls == 3, "fallback should continue serving requests");
    }

    private static void availabilityProbeFailureFallsThroughToNextProvider() {
        ReasoningProvider brokenProbe = new ReasoningProvider() {
            public String id() { return "broken-probe"; }
            public boolean available() { throw new RuntimeException("availability probe crashed"); }
            public ReasoningResult reason(ReasoningRequest request) { throw new AssertionError("provider with broken availability probe must not reason"); }
        };
        StubProvider fallback = new StubProvider("fallback", new ReasoningResult("fallback", "ok", null), false);
        PolicyProviderRouter router = new PolicyProviderRouter(List.of(
                new ProviderRoute(brokenProbe, ProviderTier.FREE_LOCAL, 1),
                new ProviderRoute(fallback, ProviderTier.FREE_LOCAL, 2)
        ), false, 2);
        ReasoningResult out;
        try {
            out = router.reason(new ReasoningRequest("hi", "", List.of()));
        } catch (RuntimeException escaped) {
            throw new AssertionError("availability probe failure must not crash provider routing", escaped);
        }
        check("fallback".equals(out.providerId()), "availability failure should fall through to the next permitted provider");
        check(fallback.calls == 1, "fallback should serve the request exactly once");
        check(router.failureCount("broken-probe") == 1, "availability probe failure should count toward circuit breaking");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class StubProvider implements ReasoningProvider {
        private final String id;
        private final ReasoningResult result;
        private final boolean fail;
        private int calls;

        private StubProvider(String id, ReasoningResult result, boolean fail) {
            this.id = id;
            this.result = result;
            this.fail = fail;
        }

        public String id() { return id; }
        public boolean available() { return true; }
        public ReasoningResult reason(ReasoningRequest request) {
            calls++;
            if (fail) throw new RuntimeException("boom");
            return result;
        }
    }
}