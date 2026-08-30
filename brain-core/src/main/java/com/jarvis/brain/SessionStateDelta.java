package com.jarvis.brain;

import java.util.Map;

/** Optional cortex-authored semantic updates for the currently open conversation only. */
public record SessionStateDelta(String activeGoal, String activeTopic, Map<String,String> entities,
                                String preference, String unresolvedQuestion, String decision,
                                boolean clearUnresolvedQuestion) {
    public SessionStateDelta {
        activeGoal = clean(activeGoal);
        activeTopic = clean(activeTopic);
        entities = entities == null ? Map.of() : Map.copyOf(entities);
        preference = clean(preference);
        unresolvedQuestion = clean(unresolvedQuestion);
        decision = clean(decision);
    }

    public static SessionStateDelta empty() {
        return new SessionStateDelta("", "", Map.of(), "", "", "", false);
    }

    public boolean isEmpty() {
        return activeGoal.isEmpty() && activeTopic.isEmpty() && entities.isEmpty() && preference.isEmpty()
                && unresolvedQuestion.isEmpty() && decision.isEmpty() && !clearUnresolvedQuestion;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
