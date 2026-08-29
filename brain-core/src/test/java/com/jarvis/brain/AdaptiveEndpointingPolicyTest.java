package com.jarvis.brain;

public final class AdaptiveEndpointingPolicyTest {
    public static void main(String[] args) {
        AdaptiveEndpointingPolicy policy = new AdaptiveEndpointingPolicy();
        check(policy.completeSilenceMillis("") >= 2200L, "blank/early speech gets patient endpoint");
        check(policy.completeSilenceMillis("I need to uh") >= 2800L, "hesitation gets extended pause");
        check(policy.completeSilenceMillis("find dinner near me") >= 1800L, "ordinary utterance tolerates natural pause");
        check(policy.completeSilenceMillis("yes") <= 1600L, "short decisive reply can finish quickly");
        check(policy.possiblyCompleteSilenceMillis("I need to uh") < policy.completeSilenceMillis("I need to uh"), "possible endpoint precedes final endpoint");
        check(policy.minimumUtteranceMillis() >= 700L, "minimum utterance avoids hair-trigger cutoff");
        System.out.println("AdaptiveEndpointingPolicyTest passed");
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
