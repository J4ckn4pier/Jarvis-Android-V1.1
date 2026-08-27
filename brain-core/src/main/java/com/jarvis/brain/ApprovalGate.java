package com.jarvis.brain;

import java.util.HashMap;
import java.util.Map;

public final class ApprovalGate {
    private final Map<String, Integer> approvals = new HashMap<>();

    public void approve(String toolName) {
        approvals.merge(toolName, 1, Integer::sum);
    }

    public boolean consume(String toolName) {
        int count = approvals.getOrDefault(toolName, 0);
        if (count <= 0) return false;
        if (count == 1) approvals.remove(toolName); else approvals.put(toolName, count - 1);
        return true;
    }
}
