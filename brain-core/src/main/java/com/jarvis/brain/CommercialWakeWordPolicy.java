package com.jarvis.brain;

import java.util.Locale;

/** Release gate for passive-wake model artifacts. Unknown or noncommercial provenance fails closed. */
public final class CommercialWakeWordPolicy {
    public record Decision(boolean approved, String reason) { }

    public Decision approve(WakeWordModelDescriptor descriptor) {
        if (descriptor == null) return reject("wake model metadata is missing");
        if (descriptor.identifier().isBlank()) return reject("wake model identifier is missing");
        if (descriptor.sha256().isBlank()) return reject("wake model artifact is not integrity pinned");
        if (descriptor.license().isBlank()) return reject("wake model license is missing");
        if (!descriptor.commercialRedistributionAllowed()) return reject("commercial redistribution is not approved");
        if (!descriptor.trainingDataProvenanceVerified()) return reject("training-data provenance is not verified");

        String license = descriptor.license().toLowerCase(Locale.ROOT);
        if (license.contains("by-nc") || license.contains("noncommercial") || license.equals("unknown")) {
            return reject("wake model license is noncommercial or unknown");
        }
        return new Decision(true, "approved");
    }

    private static Decision reject(String reason) { return new Decision(false, reason); }
}
