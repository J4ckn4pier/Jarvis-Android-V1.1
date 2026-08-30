package com.jarvis.brain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Zero-surprise-cost provider routing with circuit breaking and a hard per-request attempt budget. */
public final class PolicyProviderRouter implements ReasoningRouter {
    private final List<ProviderRoute> routes;
    private final boolean allowPaid;
    private final int failureThreshold;
    private final Duration recoveryCooldown;
    private final Clock clock;
    private final int maxAttemptsPerRequest;
    private final Map<String,Integer> consecutiveFailures = new HashMap<>();
    private final Map<String,Instant> circuitOpenedAt = new HashMap<>();

    public PolicyProviderRouter(List<ProviderRoute> routes, boolean allowPaid, int failureThreshold) {
        this(routes, allowPaid, failureThreshold, Duration.ofMinutes(5), Clock.systemUTC(), defaultBudget(routes));
    }

    public PolicyProviderRouter(List<ProviderRoute> routes, boolean allowPaid, int failureThreshold,
                                Duration recoveryCooldown, Clock clock) {
        this(routes, allowPaid, failureThreshold, recoveryCooldown, clock, defaultBudget(routes));
    }

    public PolicyProviderRouter(List<ProviderRoute> routes, boolean allowPaid, int failureThreshold,
                                Duration recoveryCooldown, Clock clock, int maxAttemptsPerRequest) {
        ArrayList<ProviderRoute> sorted = new ArrayList<>(routes == null ? List.of() : routes);
        sorted.sort(Comparator.comparing((ProviderRoute r) -> r.tier().ordinal()).thenComparingInt(ProviderRoute::priority));
        this.routes = List.copyOf(sorted);
        this.allowPaid = allowPaid;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.recoveryCooldown = recoveryCooldown == null || recoveryCooldown.isNegative() ? Duration.ZERO : recoveryCooldown;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.maxAttemptsPerRequest = Math.max(1, maxAttemptsPerRequest);
    }

    @Override
    public synchronized ReasoningResult reason(ReasoningRequest request) {
        Instant now = clock.instant();
        int attempts = 0;
        for (ProviderRoute route : routes) {
            if (attempts >= maxAttemptsPerRequest) break;
            if (route.tier() == ProviderTier.PAID_EXTERNAL && !allowPaid) continue;
            ReasoningProvider provider = route.provider();
            String providerId;
            try {
                providerId = provider.id();
            } catch (RuntimeException identityFailure) {
                continue;
            }
            if (!eligibleForAttempt(providerId, now)) continue;
            boolean available;
            try {
                available = provider.available();
            } catch (RuntimeException failure) {
                recordFailure(providerId, now);
                continue;
            }
            if (!available) continue;
            attempts++;
            try {
                ReasoningResult result = provider.reason(request);
                if (isUsable(result)) {
                    clearFailureState(providerId);
                    return result;
                }
                recordFailure(providerId, now);
            } catch (RuntimeException failure) {
                recordFailure(providerId, now);
            }
        }
        return new ReasoningResult("none", "No permitted reasoning provider is currently available.", null);
    }

    private static boolean isUsable(ReasoningResult result) {
        return result != null && (!result.text().isBlank() || result.plan() != null);
    }

    private boolean eligibleForAttempt(String providerId, Instant now) {
        int failures = consecutiveFailures.getOrDefault(providerId, 0);
        if (failures < failureThreshold) return true;
        Instant opened = circuitOpenedAt.get(providerId);
        return opened != null && !now.isBefore(opened.plus(recoveryCooldown));
    }

    private void recordFailure(String providerId, Instant now) {
        int failures = consecutiveFailures.merge(providerId, 1, Integer::sum);
        if (failures >= failureThreshold) circuitOpenedAt.put(providerId, now);
    }

    private void clearFailureState(String providerId) {
        consecutiveFailures.remove(providerId);
        circuitOpenedAt.remove(providerId);
    }

    public synchronized int failureCount(String providerId) { return consecutiveFailures.getOrDefault(providerId, 0); }
    public int maxAttemptsPerRequest() { return maxAttemptsPerRequest; }

    private static int defaultBudget(List<ProviderRoute> routes) {
        int count = routes == null ? 0 : routes.size();
        return Math.max(1, count);
    }
}