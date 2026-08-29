package com.jarvis.brain;

import java.util.Set;

public final class CommercialWakeWordPolicyTest {
    public static void main(String[] args) {
        WakeWordModelDescriptor owned = new WakeWordModelDescriptor(
                "jarvis-owned-v1", "abc123", "JARVIS-PROPRIETARY", true, true);

        CommercialWakeWordPolicy defaultPolicy = new CommercialWakeWordPolicy();
        check(!defaultPolicy.approve(owned).approved(),
                "metadata must not self-authorize; default release policy needs explicit code-level provenance approval");

        CommercialWakeWordPolicy approvedPolicy = new CommercialWakeWordPolicy(Set.of(
                CommercialWakeWordPolicy.provenanceFingerprint(owned)));
        check(approvedPolicy.approve(owned).approved(),
                "legally reviewed, hash-pinned, code-approved model should pass");

        WakeWordModelDescriptor nonCommercial = new WakeWordModelDescriptor(
                "stock-hey-jarvis", "abc123", "CC BY-NC-SA 4.0", false, true);
        CommercialWakeWordPolicy forgedNcApproval = new CommercialWakeWordPolicy(Set.of(
                CommercialWakeWordPolicy.provenanceFingerprint(nonCommercial)));
        check(!forgedNcApproval.approve(nonCommercial).approved(),
                "code allowlist must never override noncommercial license semantics");

        WakeWordModelDescriptor unknown = new WakeWordModelDescriptor(
                "unknown-model", "abc123", "UNKNOWN", false, true);
        check(!defaultPolicy.approve(unknown).approved(), "unknown redistribution rights must fail closed");

        WakeWordModelDescriptor unpinned = new WakeWordModelDescriptor(
                "permissive-but-unpinned", "", "Apache-2.0", true, true);
        check(!defaultPolicy.approve(unpinned).approved(), "shipping model must be integrity pinned");

        WakeWordModelDescriptor unverifiedTraining = new WakeWordModelDescriptor(
                "unclear-training-data", "abc123", "Apache-2.0", true, false);
        check(!defaultPolicy.approve(unverifiedTraining).approved(), "commercial model must have verified training-data provenance");

        System.out.println("CommercialWakeWordPolicyTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
