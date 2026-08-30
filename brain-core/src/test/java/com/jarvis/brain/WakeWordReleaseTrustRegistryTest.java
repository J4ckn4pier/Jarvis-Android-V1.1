package com.jarvis.brain;

public final class WakeWordReleaseTrustRegistryTest {
    public static void main(String[] args) {
        WakeWordModelDescriptor claimedOwned = new WakeWordModelDescriptor(
                "jarvis-owned-v1", "abc123", "JARVIS-PROPRIETARY", true, true);
        check(!WakeWordReleaseTrustRegistry.currentPolicy().approve(claimedOwned).approved(),
                "beta trust registry must remain empty until a wake model receives explicit legal/provenance review");
        System.out.println("WakeWordReleaseTrustRegistryTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
