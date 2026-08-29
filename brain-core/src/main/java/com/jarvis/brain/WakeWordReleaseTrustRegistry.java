package com.jarvis.brain;

import java.util.Set;

/**
 * Single release-owned trust registry for wake models that have completed license and training-data review.
 * The beta registry is intentionally empty; adding an entry is a release/legal decision, not runtime metadata.
 */
public final class WakeWordReleaseTrustRegistry {
    private WakeWordReleaseTrustRegistry() { }

    public static CommercialWakeWordPolicy currentPolicy() {
        return new CommercialWakeWordPolicy(Set.of());
    }
}
