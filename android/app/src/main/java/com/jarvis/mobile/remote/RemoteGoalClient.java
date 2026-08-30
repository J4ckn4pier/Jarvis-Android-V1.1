package com.jarvis.mobile.remote;

import com.jarvis.brain.EndpointTransportPolicy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Thin phone-side client for the stable, implementation-neutral long-running goal contract. */
public final class RemoteGoalClient {
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final String GOALS_PATH = "/v1/goals";
    private static final String PROJECTS_PATH = "/v1/projects/";
    private static final String AFTER_EVENT_QUERY = "after_event_id=";

    private final String baseUrl;
    private final String bearerToken;

    public RemoteGoalClient(String baseUrl, String bearerToken) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.bearerToken = requireToken(bearerToken);
    }

    public GoalSubmission submitGoal(
            String goal,
            String sessionId,
            List<String> constraints,
            List<String> acceptanceCriteria,
            String deadline) throws RemoteGoalException {
        JSONObject body = new JSONObject();
        put(body, "goal", requireText(goal, "goal"));
        put(body, "session_id", sessionId == null ? "primary" : sessionId);
        put(body, "constraints", stringArray(constraints));
        put(body, "acceptance_criteria", stringArray(acceptanceCriteria));
        put(body, "deadline", deadline == null ? JSONObject.NULL : deadline);
        JSONObject json = request("POST", GOALS_PATH, null, body);
        requireNoImplementationExposure(json);
        return new GoalSubmission(
                requiredString(json, "project_id"),
                requiredString(json, "session_id"),
                requiredString(json, "state"),
                requiredString(json, "goal"));
    }

    public ProjectStatus getProject(String projectId) throws RemoteGoalException {
        JSONObject json = request("GET", projectPath(projectId), null, null);
        requireNoImplementationExposure(json);
        JSONObject counts = json.optJSONObject("task_states");
        Map<String, Integer> taskStates = new LinkedHashMap<>();
        if (counts != null) {
            Iterator<String> keys = counts.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                taskStates.put(key, counts.optInt(key, 0));
            }
        }
        return new ProjectStatus(
                requiredString(json, "project_id"),
                requiredString(json, "session_id"),
                requiredString(json, "goal"),
                requiredString(json, "state"),
                json.optInt("task_count", 0),
                Collections.unmodifiableMap(taskStates),
                requiredString(json, "last_progress_at"));
    }

    public EventPage getEvents(String projectId, String afterEventId) throws RemoteGoalException {
        String query = null;
        if (afterEventId != null) {
            query = AFTER_EVENT_QUERY + URLEncoder.encode(afterEventId, StandardCharsets.UTF_8);
        }
        JSONObject json = request("GET", projectPath(projectId) + "/events", query, null);
        JSONArray values = json.optJSONArray("events");
        List<ProjectEvent> events = new ArrayList<>();
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONObject event = values.optJSONObject(i);
                if (event == null) {
                    throw new RemoteGoalException(-1, "Remote JARVIS returned an invalid event response.");
                }
                events.add(new ProjectEvent(
                        requiredString(event, "event_id"),
                        requiredString(event, "project_id"),
                        requiredString(event, "kind"),
                        event.isNull("task_id") ? null : event.optString("task_id", null),
                        requiredString(event, "timestamp")));
            }
        }
        String next = json.isNull("next_event_id") ? null : json.optString("next_event_id", null);
        return new EventPage(
                requiredString(json, "project_id"),
                List.copyOf(events),
                next,
                json.optBoolean("has_more", false));
    }

    public ApprovalDecision respondToApproval(
            String projectId, String approvalId, boolean approved, String response) throws RemoteGoalException {
        JSONObject body = new JSONObject();
        put(body, "approved", approved);
        put(body, "response", response == null ? JSONObject.NULL : response);
        JSONObject json = request(
                "POST",
                projectPath(projectId) + "/approvals/" + pathSegment(approvalId, "approval_id"),
                null,
                body);
        return new ApprovalDecision(
                requiredString(json, "project_id"),
                requiredString(json, "approval_id"),
                json.optBoolean("approved", false),
                json.isNull("response") ? null : json.optString("response", null));
    }

    public Cancellation cancel(String projectId) throws RemoteGoalException {
        JSONObject json = request("POST", projectPath(projectId) + "/cancel", null, new JSONObject());
        return new Cancellation(requiredString(json, "project_id"), requiredString(json, "state"));
    }

    public GoalResult getResult(String projectId) throws RemoteGoalException {
        JSONObject json = request("GET", projectPath(projectId) + "/result", null, null);
        requireNoImplementationExposure(json);
        return new GoalResult(
                requiredString(json, "project_id"),
                requiredString(json, "state"),
                requiredString(json, "result"));
    }

    private JSONObject request(String method, String path, String query, JSONObject body) throws RemoteGoalException {
        HttpURLConnection connection = null;
        try {
            String endpoint = baseUrl + path + (query == null || query.isBlank() ? "" : "?" + query);
            connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(45_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            String text = read(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                throw new RemoteGoalException(status, messageFor(status, text));
            }
            if (text.isBlank()) throw new RemoteGoalException(status, "Remote JARVIS returned an empty response.");
            return new JSONObject(text);
        } catch (RemoteGoalException expected) {
            throw expected;
        } catch (Exception failure) {
            throw new RemoteGoalException(-1, "Remote JARVIS is unavailable right now.", failure);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void put(JSONObject json, String key, Object value) throws RemoteGoalException {
        try {
            json.put(key, value);
        } catch (JSONException failure) {
            throw new RemoteGoalException(-1, "Unable to encode the remote JARVIS request.", failure);
        }
    }

    private static String messageFor(int status, String body) {
        if (status == 401) return "Remote JARVIS authorization was rejected.";
        if (status == 422) return "Remote JARVIS rejected the goal request.";
        if (status == 503) return "Remote JARVIS is temporarily unavailable.";
        if (body == null || body.isBlank()) return "Remote JARVIS request failed with HTTP " + status + ".";
        return "Remote JARVIS request failed with HTTP " + status + ".";
    }

    private static String normalizeBaseUrl(String value) {
        String endpoint = requireText(value, "base_url");
        while (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        if (!EndpointTransportPolicy.allows(endpoint)) {
            throw new IllegalArgumentException("HTTPS or local mDNS/loopback transport is required");
        }
        return endpoint;
    }

    private static String requireToken(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Bearer token is required");
        if (!token.equals(token.trim())) throw new IllegalArgumentException("Bearer token must not contain edge whitespace");
        return token;
    }

    private static String projectPath(String projectId) {
        return PROJECTS_PATH + pathSegment(projectId, "project_id");
    }

    private static String pathSegment(String value, String field) {
        return URLEncoder.encode(requireText(value, field), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static JSONArray stringArray(List<String> values) {
        JSONArray array = new JSONArray();
        if (values == null) return array;
        for (String value : values) {
            if (value == null) continue;
            array.put(value);
        }
        return array;
    }

    private static String requiredString(JSONObject json, String key) throws RemoteGoalException {
        String value = json.optString(key, "");
        if (value.isBlank()) throw new RemoteGoalException(-1, "Remote JARVIS response is missing " + key + ".");
        return value;
    }

    private static void requireNoImplementationExposure(JSONObject json) throws RemoteGoalException {
        if (!json.has("provider_details_exposed") || json.optBoolean("provider_details_exposed", true)) {
            throw new RemoteGoalException(-1, "Remote JARVIS returned an unsafe internal-detail response.");
        }
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > MAX_RESPONSE_BYTES) throw new IllegalStateException("Remote response too large");
            out.write(buffer, 0, count);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public record GoalSubmission(String projectId, String sessionId, String state, String goal) {}
    public record ProjectStatus(String projectId, String sessionId, String goal, String state,
                                int taskCount, Map<String, Integer> taskStates, String lastProgressAt) {}
    public record ProjectEvent(String eventId, String projectId, String kind, String taskId, String timestamp) {}
    public record EventPage(String projectId, List<ProjectEvent> events, String nextEventId, boolean hasMore) {}
    public record ApprovalDecision(String projectId, String approvalId, boolean approved, String response) {}
    public record Cancellation(String projectId, String state) {}
    public record GoalResult(String projectId, String state, String result) {}

    public static final class RemoteGoalException extends Exception {
        private final int statusCode;
        public RemoteGoalException(int statusCode, String message) { super(Objects.requireNonNull(message)); this.statusCode = statusCode; }
        public RemoteGoalException(int statusCode, String message, Throwable cause) { super(Objects.requireNonNull(message), cause); this.statusCode = statusCode; }
        public int statusCode() { return statusCode; }
        public boolean retryable() { return statusCode == -1 || statusCode == 503; }
    }
}
