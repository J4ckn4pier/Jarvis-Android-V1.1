package com.jarvis.brain;

public final class CommercialWakeWordPolicyTest {
    public static void main(String[] args) {
        CommercialWakeWordPolicy policy = new CommercialWakeWordPolicy();

        WakeWordModelDescriptor owned = new WakeWordModelDescriptor(
                "jarvis-owned-v1", "abc123", "JARVIS-PROPRIETARY", true, true);
        check(policy.approve(owned).approved(), "owned, hash-pinned, commercially redistributable model should pass");

        WakeWordModelDescriptor nonCommercial = new WakeWordModelDescriptor(
                "stock-hey-jarvis", "abc123", "CC BY-NC-SA 4.0", false, true);
        check(!policy.approve(nonCommercial).approved(), "noncommercial wake model must be rejected");

        WakeWordModelDescriptor unknown = new WakeWordModelDescriptor(
                "unknown-model", "abc123", "UNKNOWN", false, true);
        check(!policy.approve(unknown).approved(), "unknown redistribution rights must fail closed");

        WakeWordModelDescriptor unpinned = new WakeWordModelDescriptor(
                "permissive-but-unpinned", "", "Apache-2.0", true, true);
        check(!policy.approve(unpinned).approved(), "shipping model must be integrity pinned");

        WakeWordModelDescriptor unverifiedTraining = new WakeWordModelDescriptor(
                "unclear-training-data", "abc123", "Apache-2.0", true, false);
        check(!policy.approve(unverifiedTraining).approved(), "commercial model must have verified training-data provenance");

        System.out.println("CommercialWakeWordPolicyTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
