package com.jarvis.brain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider router that preserves JARVIS's zero-surprise-cost policy and temporarily
 * suppresses repeatedly failing providers, then probes them again after a cooldown.
 */
public final class PolicyProviderRouter implements ReasoningRouter {
    private final List<ProviderRoute> routes;
    private final boolean allowPaid;
    private final int failureThreshold;
    private final Duration recoveryCooldown;
    private final Clock clock;
    private final Map<String, Integer> consecutiveFailures = new HashMap<>();
    private final Map<String, Instant> circuitOpenedAt = new HashMap<>();

    public PolicyProviderRouter(List<ProviderRoute> routes, boolean allowPaid, int failureThreshold) {
        this(routes, allowPaid, failureThreshold, Duration.ofMinutes(5), Clock.systemUTC());
    }

    public PolicyProviderRouter(List<ProviderRoute> routes, boolean allowPaid, int failureThreshold,
                                Duration recoveryCooldown, Clock clock) {
        ArrayList<ProviderRoute> sorted = new ArrayList<>(routes == null ? List.of() : routes);
        sorted.sort(Comparator.comparing((ProviderRoute r) -> r.tier().ordinal())
                .thenComparingInt(ProviderRoute::priority));
        this.routes = List.copyOf(sorted);
        this.allowPaid = allowPaid;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.recoveryCooldown = recoveryCooldown == null || recoveryCooldown.isNegative()
                ? Duration.ZERO : recoveryCooldown;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public synchronized ReasoningResult reason(ReasoningRequest request) {
        Instant now = clock.instant();
        for (ProviderRoute route : routes) {
            if (route.tier() == ProviderTier.PAID_EXTERNAL && !allowPaid) continue;
            ReasoningProvider provider = route.provider();
            if (!provider.available()) continue;
            if (!eligibleForAttempt(provider.id(), now)) continue;

            try {
                ReasoningResult result = provider.reason(request);
                if (result != null) {
                    clearFailureState(provider.id());
                    return result;
                }
                recordFailure(provider.id(), now);
            } catch (RuntimeException failure) {
                recordFailure(provider.id(), now);
            }
        }
        return new ReasoningResult("none", "No permitted reasoning provider is currently available.", null);
    }

    private boolean eligibleForAttempt(String providerId, Instant now) {
        int failures = consecutiveFailures.getOrDefault(providerId, 0);
        if (failures < failureThreshold) return true;
        Instant opened = circuitOpenedAt.get(providerId);
        if (opened == null) return false;
        return !now.isBefore(opened.plus(recoveryCooldown));
    }

    private void recordFailure(String providerId, Instant now) {
        int failures = consecutiveFailures.merge(providerId, 1, Integer::sum);
        if (failures >= failureThreshold) {
            // A failed recovery probe re-opens the circuit from the current attempt time.
            circuitOpenedAt.put(providerId, now);
        }
    }

    private void clearFailureState(String providerId) {
        consecutiveFailures.remove(providerId);
        circuitOpenedAt.remove(providerId);
    }

    public synchronized int failureCount(String providerId) {
        return consecutiveFailures.getOrDefault(providerId, 0);
    }
}
