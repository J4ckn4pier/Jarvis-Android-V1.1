package com.jarvis.brain;

import java.util.Set;

/**
 * Release-owned trust registry for passive wake dependencies.
 *
 * Redistributed model artifacts remain governed by CommercialWakeWordPolicy fingerprint approval.
 * Android platform speech services are a separate case: JARVIS does not redistribute their model
 * bytes, so release approval is an explicit service-identifier allowlist plus truthful descriptor
 * invariants rather than a fabricated artifact hash.
 */
public final class WakeWordReleaseTrustRegistry {
    private static final Set<String> APPROVED_PLATFORM_SERVICES = Set.of(
            "android-on-device-speech-platform",
            "android-system-speech-platform");

    private WakeWordReleaseTrustRegistry() { }

    /** Beta ships no third-party/custom wake model artifact until its exact fingerprint is reviewed. */
    public static CommercialWakeWordPolicy currentPolicy() {
        return new CommercialWakeWordPolicy(Set.of());
    }

    /**
     * Approves only known Android-owned speech service identities and only when the descriptor makes
     * clear that JARVIS ships no model artifact and claims no redistribution right over platform bits.
     */
    public static boolean isPlatformManagedServiceApproved(WakeWordModelDescriptor descriptor) {
        if (descriptor == null) return false;
        String identifier = descriptor.identifier().trim().toLowerCase(java.util.Locale.ROOT);
        return APPROVED_PLATFORM_SERVICES.contains(identifier)
                && descriptor.sha256().isBlank()
                && "platform-managed-not-redistributed".equalsIgnoreCase(descriptor.license())
                && !descriptor.commercialRedistributionAllowed()
                && descriptor.trainingDataProvenanceVerified();
    }
}
