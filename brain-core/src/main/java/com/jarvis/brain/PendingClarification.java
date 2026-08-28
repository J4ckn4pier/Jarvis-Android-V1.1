package com.jarvis.brain;

import java.util.List;

record PendingClarification(Plan plan, List<MissingArgument> missing) {
    record MissingArgument(int stepIndex, String argument) {}
    PendingClarification {
        missing = missing == null ? List.of() : List.copyOf(missing);
    }
}
