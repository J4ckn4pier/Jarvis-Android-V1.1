package com.jarvis.brain;

import java.util.Locale;
import java.util.Set;

/** Release gate for passive-wake model artifacts. Metadata cannot self-authorize commercial use. */
public final class CommercialWakeWordPolicy {
    public record Decision(boolean approved, String reason) { }

    private final Set<String> approvedProvenanceFingerprints;

    /** Default release policy approves no model until a legally reviewed fingerprint is compiled in. */
    public CommercialWakeWordPolicy() { this(Set.of()); }

    public CommercialWakeWordPolicy(Set<String> approvedProvenanceFingerprints) {
        this.approvedProvenanceFingerprints = approvedProvenanceFingerprints == null
                ? Set.of()
                : Set.copyOf(approvedProvenanceFingerprints);
    }

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
        if (!approvedProvenanceFingerprints.contains(provenanceFingerprint(descriptor))) {
            return reject("wake model provenance has not been approved in the release trust registry");
        }
        return new Decision(true, "approved");
    }

    public static String provenanceFingerprint(WakeWordModelDescriptor descriptor) {
        if (descriptor == null) return "";
        return descriptor.identifier().trim().toLowerCase(Locale.ROOT)
                + "|" + descriptor.sha256().trim().toLowerCase(Locale.ROOT)
                + "|" + descriptor.license().trim().toLowerCase(Locale.ROOT);
    }

    private static Decision reject(String reason) { return new Decision(false, reason); }
}
