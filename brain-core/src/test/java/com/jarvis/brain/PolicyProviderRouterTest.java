package com.jarvis.brain;

import java.util.List;

public final class PolicyProviderRouterTest {
    private static int checks = 0;

    public static void main(String[] args) {
        freeLocalWinsOverPaid();
        paidIsBlockedWithoutOptIn();
        repeatedFailuresOpenCircuit();
        availabilityProbeFailureFallsThroughToNextProvider();
        identityProbeFailureFallsThroughToNextProvider();
        emptyProviderResultFallsThroughToNextProvider();
        emptyPlanFallsThroughToNextProvider();
        planOnlyProviderResultIsUsable();
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

    private static void identityProbeFailureFallsThroughToNextProvider() {
        ReasoningProvider brokenIdentity = new ReasoningProvider() {
            public String id() { throw new RuntimeException("provider identity crashed"); }
            public boolean available() { throw new AssertionError("provider with broken identity must not be probed further"); }
            public ReasoningResult reason(ReasoningRequest request) { throw new AssertionError("provider with broken identity must not reason"); }
        };
        StubProvider fallback = new StubProvider("fallback", new ReasoningResult("fallback", "ok", null), false);
        PolicyProviderRouter router = new PolicyProviderRouter(List.of(
                new ProviderRoute(brokenIdentity, ProviderTier.FREE_LOCAL, 1),
                new ProviderRoute(fallback, ProviderTier.FREE_LOCAL, 2)
        ), false, 2);
        ReasoningResult out;
        try {
            out = router.reason(new ReasoningRequest("hi", "", List.of()));
        } catch (RuntimeException escaped) {
            throw new AssertionError("provider identity failure must not crash provider routing", escaped);
        }
        check("fallback".equals(out.providerId()), "identity failure should fall through to the next permitted provider");
        check(fallback.calls == 1, "fallback should serve the request exactly once after identity failure");
    }

    private static void emptyProviderResultFallsThroughToNextProvider() {
        StubProvider empty = new StubProvider("empty", new ReasoningResult("empty", "   ", null), false);
        StubProvider fallback = new StubProvider("fallback", new ReasoningResult("fallback", "usable answer", null), false);
        PolicyProviderRouter router = new PolicyProviderRouter(List.of(
                new ProviderRoute(empty, ProviderTier.FREE_LOCAL, 1),
                new ProviderRoute(fallback, ProviderTier.FREE_LOCAL, 2)
        ), false, 2);
        ReasoningResult out = router.reason(new ReasoningRequest("hi", "", List.of()));
        check("fallback".equals(out.providerId()), "an empty response without a plan must not block a healthy fallback");
        check(empty.calls == 1, "empty provider should be attempted once");
        check(fallback.calls == 1, "fallback should serve after unusable empty response");
        check(router.failureCount("empty") == 1, "unusable empty response should count toward circuit breaking");
    }

    private static void emptyPlanFallsThroughToNextProvider() {
        StubProvider emptyPlanner = new StubProvider("empty-planner",
                new ReasoningResult("empty-planner", "done", new Plan("claim work", List.of())), false);
        StubProvider fallback = new StubProvider("fallback", new ReasoningResult("fallback", "usable answer", null), false);
        PolicyProviderRouter router = new PolicyProviderRouter(List.of(
                new ProviderRoute(emptyPlanner, ProviderTier.FREE_LOCAL, 1),
                new ProviderRoute(fallback, ProviderTier.FREE_LOCAL, 2)
        ), false, 2);
        ReasoningResult out = router.reason(new ReasoningRequest("do the work", "", List.of()));
        check("fallback".equals(out.providerId()),
                "a provider response whose action plan contains zero steps must not block a healthy fallback provider");
        check(emptyPlanner.calls == 1 && fallback.calls == 1,
                "router should attempt the malformed planner once, then use the next permitted provider");
        check(router.failureCount("empty-planner") == 1,
                "structurally empty provider plan should count toward provider circuit breaking");
    }

    private static void planOnlyProviderResultIsUsable() {
        Plan plan = new Plan("open settings", List.of(new PlanStep("open_app")));
        StubProvider planner = new StubProvider("planner", new ReasoningResult("planner", null, plan), false);
        StubProvider fallback = new StubProvider("fallback", new ReasoningResult("fallback", "fallback answer", null), false);
        PolicyProviderRouter router = new PolicyProviderRouter(List.of(
                new ProviderRoute(planner, ProviderTier.FREE_LOCAL, 1),
                new ProviderRoute(fallback, ProviderTier.FREE_LOCAL, 2)
        ), false, 2);
        ReasoningResult out = router.reason(new ReasoningRequest("open settings", "", List.of()));
        check("planner".equals(out.providerId()),
                "a provider plan is usable even when that provider has no conversational text to attach");
        check(out.plan() == plan, "router should preserve the valid provider plan unchanged");
        check(fallback.calls == 0, "a valid plan-only result must not waste an attempt on fallback reasoning");
        check(router.failureCount("planner") == 0, "valid plan-only output must not count as a provider failure");
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