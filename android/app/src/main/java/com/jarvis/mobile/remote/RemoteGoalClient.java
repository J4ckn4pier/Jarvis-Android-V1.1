package com.jarvis.mobile.remote;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Provider-neutral Android boundary for the stable JARVIS management contract. */
public interface RemoteGoalClient {
    record GoalRequest(String goal, String sessionId, List<String> constraints,
                       List<String> acceptanceCriteria, String deadline) {}
    record GoalSubmission(String projectId, String sessionId, String state, String goal) {}
    record ProjectStatus(String projectId, String sessionId, String goal, String state,
                         int taskCount, Map<String, Integer> taskStates, String lastProgressAt) {}
    record Event(String eventId, String projectId, String kind, String taskId, String timestamp) {}
    record EventPage(String projectId, List<Event> events, String nextEventId, boolean hasMore) {}
    record ApprovalDecision(String projectId, String approvalId, boolean approved, String response) {}
    record Cancellation(String projectId, String state) {}
    record GoalResult(String projectId, String state, String result) {}

    GoalSubmission submit(GoalRequest request) throws IOException;
    ProjectStatus project(String projectId) throws IOException;
    EventPage events(String projectId, String afterEventId, int limit) throws IOException;
    ApprovalDecision approve(String projectId, String approvalId, boolean approved, String response) throws IOException;
    Cancellation cancel(String projectId) throws IOException;
    GoalResult result(String projectId) throws IOException;
}
