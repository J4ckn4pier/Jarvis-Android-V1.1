package com.jarvis.brain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Release gate for passive-wake model artifacts. Metadata cannot self-authorize commercial use. */
public final class CommercialWakeWordPolicy {
    public record Decision(boolean approved, String reason) { }

    private final Set<String> approvedProvenanceFingerprints;
    private final Map<String, Long> minimumApprovedRevisionByIdentifier;

    /** Default release policy approves no model until a legally reviewed fingerprint is compiled in. */
    public CommercialWakeWordPolicy() { this(Set.of(), Map.of()); }

    /** Compatibility constructor for release policies that have not yet advanced a revision floor. */
    public CommercialWakeWordPolicy(Set<String> approvedProvenanceFingerprints) {
        this(approvedProvenanceFingerprints, Map.of());
    }

    /**
     * Release-owned approval policy. A minimum revision is an anti-downgrade floor for a model identifier.
     * Explicit rollback requires a new release policy that deliberately lowers that floor.
     */
    public CommercialWakeWordPolicy(
            Set<String> approvedProvenanceFingerprints,
            Map<String, Long> minimumApprovedRevisionByIdentifier) {
        this.approvedProvenanceFingerprints = approvedProvenanceFingerprints == null
                ? Set.of()
                : Set.copyOf(approvedProvenanceFingerprints);
        if (minimumApprovedRevisionByIdentifier == null || minimumApprovedRevisionByIdentifier.isEmpty()) {
            this.minimumApprovedRevisionByIdentifier = Map.of();
        } else {
            java.util.HashMap<String, Long> normalized = new java.util.HashMap<>();
            for (Map.Entry<String, Long> entry : minimumApprovedRevisionByIdentifier.entrySet()) {
                String identifier = normalizeIdentifier(entry.getKey());
                if (identifier.isBlank()) continue;
                long revision = entry.getValue() == null ? 1L : Math.max(1L, entry.getValue());
                normalized.put(identifier, revision);
            }
            this.minimumApprovedRevisionByIdentifier = Map.copyOf(normalized);
        }
    }

    public Decision approve(WakeWordModelDescriptor descriptor) {
        if (descriptor == null) return reject("wake model metadata is missing");
        if (descriptor.identifier().isBlank()) return reject("wake model identifier is missing");
        if (descriptor.revision() < 1) return reject("wake model revision is invalid");
        if (descriptor.sha256().isBlank()) return reject("wake model artifact is not integrity pinned");
        if (descriptor.license().isBlank()) return reject("wake model license is missing");
        if (!descriptor.commercialRedistributionAllowed()) return reject("commercial redistribution is not approved");
        if (!descriptor.trainingDataProvenanceVerified()) return reject("training-data provenance is not verified");

        String license = descriptor.license().toLowerCase(Locale.ROOT);
        if (license.contains("by-nc") || license.contains("noncommercial") || license.equals("unknown")) {
            return reject("wake model license is noncommercial or unknown");
        }

        String identifier = normalizeIdentifier(descriptor.identifier());
        long minimumRevision = minimumApprovedRevisionByIdentifier.getOrDefault(identifier, 1L);
        if (descriptor.revision() < minimumRevision) {
            return reject("wake model revision is below the release anti-downgrade floor");
        }

        if (!approvedProvenanceFingerprints.contains(provenanceFingerprint(descriptor))) {
            return reject("wake model provenance has not been approved in the release trust registry");
        }
        return new Decision(true, "approved");
    }

    public static String provenanceFingerprint(WakeWordModelDescriptor descriptor) {
        if (descriptor == null) return "";
        return normalizeIdentifier(descriptor.identifier())
                + "|" + descriptor.revision()
                + "|" + descriptor.sha256().trim().toLowerCase(Locale.ROOT)
                + "|" + descriptor.license().trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }

    private static Decision reject(String reason) { return new Decision(false, reason); }
}
