package com.jarvis.brain;

/** Brain-side signals that may make an outcome follow-up timely. */
public enum FollowupTrigger {
    USER_RETURNED_HOME,
    USER_REOPENED_RELATED_CONTEXT,
    EXPLICIT_FOLLOWUP_REQUEST
}
