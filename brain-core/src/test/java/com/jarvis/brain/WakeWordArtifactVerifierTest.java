package com.jarvis.brain;

public final class WakeWordArtifactVerifierTest {
    public static void main(String[] args) {
        WakeWordArtifactVerifier verifier = new WakeWordArtifactVerifier(new CommercialWakeWordPolicy());
        WakeWordModelDescriptor approved = new WakeWordModelDescriptor(
                "jarvis-owned-v1", "abcd", "JARVIS-PROPRIETARY", true, true);

        check(verifier.verify(approved, "abcd").approved(), "matching approved descriptor and artifact hash should pass");
        check(!verifier.verify(approved, "dcba").approved(), "artifact hash mismatch must fail closed");
        check(!verifier.verify(approved, "").approved(), "missing calculated artifact hash must fail closed");

        WakeWordModelDescriptor nc = new WakeWordModelDescriptor(
                "stock-model", "abcd", "CC BY-NC-SA 4.0", false, true);
        check(!verifier.verify(nc, "abcd").approved(), "integrity cannot override noncommercial licensing");

        System.out.println("WakeWordArtifactVerifierTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
