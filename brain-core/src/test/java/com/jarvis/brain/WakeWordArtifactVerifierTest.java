package com.jarvis.brain;

import java.util.Set;

public final class WakeWordArtifactVerifierTest {
    public static void main(String[] args) {
        WakeWordModelDescriptor approved = new WakeWordModelDescriptor(
                "jarvis-owned-v1", "abcd", "JARVIS-PROPRIETARY", true, true);
        CommercialWakeWordPolicy policy = new CommercialWakeWordPolicy(Set.of(
                CommercialWakeWordPolicy.provenanceFingerprint(approved)));
        WakeWordArtifactVerifier verifier = new WakeWordArtifactVerifier(policy);

        check(verifier.verify(approved, "abcd").approved(), "matching code-approved descriptor and artifact hash should pass");
        check(!verifier.verify(approved, "dcba").approved(), "artifact hash mismatch must fail closed");
        check(!verifier.verify(approved, "").approved(), "missing calculated artifact hash must fail closed");

        WakeWordArtifactVerifier defaultVerifier = new WakeWordArtifactVerifier(new CommercialWakeWordPolicy());
        check(!defaultVerifier.verify(approved, "abcd").approved(),
                "matching bytes must still fail when provenance has not been explicitly approved in code");

        WakeWordModelDescriptor nc = new WakeWordModelDescriptor(
                "stock-model", "abcd", "CC BY-NC-SA 4.0", false, true);
        check(!verifier.verify(nc, "abcd").approved(), "integrity cannot override noncommercial licensing");

        System.out.println("WakeWordArtifactVerifierTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
