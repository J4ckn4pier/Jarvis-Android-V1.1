package com.jarvis.brain;

public final class GoalInterruptionPolicyTest {
    private static int checks;

    public static void main(String[] args) {
        sameGoalCorrectionCancelsAndRestarts();
        unrelatedCheapResumableWorkDefaultsToDoBoth();
        consequentialInFlightWorkLeansAskBeforeAbandoning();
        explicitUrgencyCanSwitchAwayFromSafeResumableWork();
        contextualRefinementStaysWithCurrentGoal();
        System.out.println("GoalInterruptionPolicyTest: " + checks + " assertions passed");
    }

    private static void sameGoalCorrectionCancelsAndRestarts() {
        GoalInterruptionPolicy policy = new GoalInterruptionPolicy();
        InterruptionDecision decision = policy.decide(new InterruptionContext(
                "find me a Chinese restaurant",
                "actually find me an Italian restaurant instead",
                0.92,
                true,
                false,
                false,
                0.25));
        check(decision == InterruptionDecision.RESTART_CURRENT,
                "same-goal parameter correction must cancel/restart current goal");
    }

    private static void unrelatedCheapResumableWorkDefaultsToDoBoth() {
        GoalInterruptionPolicy policy = new GoalInterruptionPolicy();
        InterruptionDecision decision = policy.decide(new InterruptionContext(
                "find dinner options near me",
                "what's on my calendar tomorrow",
                0.10,
                false,
                true,
                false,
                0.20));
        check(decision == InterruptionDecision.DO_BOTH,
                "unrelated safe resumable work should generally allow both goals");
    }

    private static void consequentialInFlightWorkLeansAskBeforeAbandoning() {
        GoalInterruptionPolicy policy = new GoalInterruptionPolicy();
        InterruptionDecision decision = policy.decide(new InterruptionContext(
                "prepare to send Mom the message",
                "find me somewhere to eat",
                0.05,
                false,
                false,
                true,
                0.15));
        check(decision == InterruptionDecision.ASK,
                "consequential or approval-bound current work should ask before being dropped");
    }

    private static void explicitUrgencyCanSwitchAwayFromSafeResumableWork() {
        GoalInterruptionPolicy policy = new GoalInterruptionPolicy();
        InterruptionDecision decision = policy.decide(new InterruptionContext(
                "compare movie options for tonight",
                "stop that, navigate me to the nearest hospital now",
                0.02,
                false,
                true,
                false,
                0.98));
        check(decision == InterruptionDecision.SWITCH,
                "urgent new goal should switch away from safe resumable work");
    }

    private static void contextualRefinementStaysWithCurrentGoal() {
        GoalInterruptionPolicy policy = new GoalInterruptionPolicy();
        InterruptionDecision decision = policy.decide(new InterruptionContext(
                "find me a restaurant for dinner",
                "somewhere quiet with outdoor seating",
                0.88,
                false,
                true,
                false,
                0.10));
        check(decision == InterruptionDecision.INCORPORATE_CONTEXT,
                "contextual refinement should be incorporated into current task rather than treated as a new goal");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
