package com.jarvis.brain;

import java.util.List;

public record CallOutcome(Status status, String confirmedTime, List<String> alternatives, String summary) {
    public enum Status { IN_PROGRESS, CONFIRMED, ALTERNATIVES_AVAILABLE, FAILED }
    public CallOutcome {
        confirmedTime = confirmedTime == null ? "" : confirmedTime;
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        summary = summary == null ? "" : summary;
    }
}
