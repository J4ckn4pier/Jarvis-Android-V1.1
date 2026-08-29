package com.jarvis.brain;

/** Commercial/provenance metadata for a passive wake model artifact. */
public record WakeWordModelDescriptor(
        String identifier,
        String sha256,
        String license,
        boolean commercialRedistributionAllowed,
        boolean trainingDataProvenanceVerified) {
    public WakeWordModelDescriptor {
        identifier = identifier == null ? "" : identifier.trim();
        sha256 = sha256 == null ? "" : sha256.trim().toLowerCase(java.util.Locale.ROOT);
        license = license == null ? "" : license.trim();
    }
}
