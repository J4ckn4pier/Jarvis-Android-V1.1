package com.jarvis.brain;

/** Commercial/provenance metadata for a passive wake model artifact. */
public record WakeWordModelDescriptor(
        String identifier,
        long revision,
        String sha256,
        String license,
        boolean commercialRedistributionAllowed,
        boolean trainingDataProvenanceVerified) {
    public WakeWordModelDescriptor {
        identifier = identifier == null ? "" : identifier.trim();
        if (revision < 1) revision = 1;
        sha256 = sha256 == null ? "" : sha256.trim().toLowerCase(java.util.Locale.ROOT);
        license = license == null ? "" : license.trim();
    }

    /** Compatibility constructor for existing revision-1 descriptors. */
    public WakeWordModelDescriptor(
            String identifier,
            String sha256,
            String license,
            boolean commercialRedistributionAllowed,
            boolean trainingDataProvenanceVerified) {
        this(identifier, 1L, sha256, license, commercialRedistributionAllowed, trainingDataProvenanceVerified);
    }
}
