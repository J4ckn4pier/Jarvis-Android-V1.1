package com.jarvis.brain;

import java.time.DayOfWeek;
import java.util.List;

public final class HabitPredictionTest {
    private static int passed;

    public static void main(String[] args) {
        repeatedRoutineProducesPrediction();
        sparseEventsDoNotCreateNoisyPrediction();
        contextMismatchLowersRelevance();
        System.out.println("PASS " + passed + " habit prediction assertions");
    }

    private static void repeatedRoutineProducesPrediction() {
        HabitLearner learner = new HabitLearner();
        for (int week = 0; week < 6; week++) {
            learner.observe(new HabitEvent("open_gym_app", DayOfWeek.MONDAY, 18, "after_work"));
            learner.observe(new HabitEvent("open_gym_app", DayOfWeek.WEDNESDAY, 18, "after_work"));
        }
        List<PredictionCandidate> predictions = learner.predict(DayOfWeek.MONDAY, 18, "after_work");
        check(predictions.stream().anyMatch(p -> p.message().contains("open_gym_app") && p.confidence() >= 0.75), "repeated routine should become high-confidence prediction");
    }

    private static void sparseEventsDoNotCreateNoisyPrediction() {
        HabitLearner learner = new HabitLearner();
        learner.observe(new HabitEvent("order_coffee", DayOfWeek.FRIDAY, 8, "home"));
        check(learner.predict(DayOfWeek.FRIDAY, 8, "home").isEmpty(), "one-off behavior should not become a proactive habit");
    }

    private static void contextMismatchLowersRelevance() {
        HabitLearner learner = new HabitLearner();
        for (int i = 0; i < 5; i++) learner.observe(new HabitEvent("start_focus_mode", DayOfWeek.TUESDAY, 9, "office"));
        PredictionCandidate match = learner.predict(DayOfWeek.TUESDAY, 9, "office").get(0);
        PredictionCandidate mismatch = learner.predict(DayOfWeek.TUESDAY, 9, "home").get(0);
        check(match.relevance() > mismatch.relevance(), "prediction relevance should fall when context mismatches learned routine");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }
}
