package com.jarvis.brain;

import java.util.List;

public record ExecutionReport(Status status, List<String> outputs, String blockedTool, String failureDetail) {
    public enum Status { COMPLETED, APPROVAL_REQUIRED, RECOVERY_REQUIRED, FAILED }

    public ExecutionReport {
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        blockedTool = blockedTool == null ? "" : blockedTool;
        failureDetail = failureDetail == null ? "" : failureDetail;
    }

    public ExecutionReport(Status status, List<String> outputs, String blockedTool) {
        this(status, outputs, blockedTool, "");
    }
}
