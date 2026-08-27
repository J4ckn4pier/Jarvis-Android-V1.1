package com.jarvis.brain;

import java.util.List;

public record ExecutionReport(Status status, List<String> outputs, String blockedTool) {
    public enum Status { COMPLETED, APPROVAL_REQUIRED, FAILED }
    public ExecutionReport { outputs = List.copyOf(outputs); }
}
