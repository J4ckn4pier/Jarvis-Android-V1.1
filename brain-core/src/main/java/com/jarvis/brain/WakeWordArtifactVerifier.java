package com.jarvis.brain;

/** Couples wake-model commercial approval to the exact integrity-pinned artifact bytes. */
public final class WakeWordArtifactVerifier {
    private final CommercialWakeWordPolicy commercialPolicy;

    public WakeWordArtifactVerifier(CommercialWakeWordPolicy commercialPolicy) {
        this.commercialPolicy = commercialPolicy == null ? new CommercialWakeWordPolicy() : commercialPolicy;
    }

    public CommercialWakeWordPolicy.Decision verify(WakeWordModelDescriptor descriptor, String calculatedSha256) {
        CommercialWakeWordPolicy.Decision commercial = commercialPolicy.approve(descriptor);
        if (!commercial.approved()) return commercial;
        String calculated = calculatedSha256 == null ? "" : calculatedSha256.trim().toLowerCase(java.util.Locale.ROOT);
        if (calculated.isBlank()) return new CommercialWakeWordPolicy.Decision(false, "wake model artifact hash is missing");
        if (!descriptor.sha256().equals(calculated)) {
            return new CommercialWakeWordPolicy.Decision(false, "wake model artifact hash does not match approved metadata");
        }
        return new CommercialWakeWordPolicy.Decision(true, "approved");
    }
}
