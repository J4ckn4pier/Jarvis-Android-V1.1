package com.jarvis.mobile.remote;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTP implementation of the M13 JARVIS APK integration contract. */
public final class HttpRemoteGoalClient implements RemoteGoalClient {
    private static final String PROVIDER_GUARD = "provider_details_exposed";
    private static final String PROVIDER_LEAK = "Provider details leaked through the JARVIS management contract";
    private final String baseUrl;
    private final String bearerToken;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public HttpRemoteGoalClient(String baseUrl, String bearerToken) {
        this(baseUrl, bearerToken, 8_000, 20_000);
    }

    public HttpRemoteGoalClient(String baseUrl, String bearerToken, int connectTimeoutMs, int readTimeoutMs) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("JARVIS base URL is required");
        if (bearerToken == null || bearerToken.isEmpty()) throw new IllegalArgumentException("JARVIS credential is required");
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        validateTransport(cleanBase);
        this.baseUrl = cleanBase;
        this.bearerToken = bearerToken; // Credential is exact: never trim or normalize it.
        this.connectTimeoutMs = Math.max(1, connectTimeoutMs);
        this.readTimeoutMs = Math.max(1, readTimeoutMs);
    }

    @Override public GoalSubmission submit(GoalRequest request) throws IOException {
        if (request == null || request.goal() == null || request.goal().isBlank()) {
            throw new IllegalArgumentException("Remote goal must be non-empty");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("goal", request.goal());
            body.put("session_id", request.sessionId() == null || request.sessionId().isEmpty() ? "primary" : request.sessionId());
            body.put("constraints", strings(request.constraints()));
            body.put("acceptance_criteria", strings(request.acceptanceCriteria()));
            if (request.deadline() != null) body.put("deadline", request.deadline());
            JSONObject json = request("POST", "/v1/goals", body);
            rejectProviderLeak(json);
            return new GoalSubmission(exact(json, "project_id"), exact(json, "session_id"), exact(json, "state"), exact(json, "goal"));
        } catch (JSONException invalid) {
            throw malformed(invalid);
        }
    }

    @Override public ProjectStatus project(String projectId) throws IOException {
        JSONObject json = request("GET", "/v1/projects/" + segment(projectId), null);
        try {
            rejectProviderLeak(json);
            JSONObject states = json.getJSONObject("task_states");
            Map<String, Integer> taskStates = new LinkedHashMap<>();
            for (String key : states.keySet()) taskStates.put(key, states.getInt(key));
            return new ProjectStatus(exact(json, "project_id"), exact(json, "session_id"), exact(json, "goal"),
                    exact(json, "state"), json.getInt("task_count"), Map.copyOf(taskStates), exact(json, "last_progress_at"));
        } catch (JSONException invalid) {
            throw malformed(invalid);
        }
    }

    @Override public EventPage events(String projectId, String afterEventId, int limit) throws IOException {
        int boundedLimit = Math.max(1, Math.min(1000, limit));
        String path = "/v1/projects/" + segment(projectId) + "/events?limit=" + boundedLimit;
        if (afterEventId != null) path += "&after_event_id=" + Uri.encode(afterEventId);
        JSONObject json = request("GET", path, null);
        try {
            JSONArray items = json.getJSONArray("events");
            List<Event> events = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                events.add(new Event(exact(item, "event_id"), exact(item, "project_id"), exact(item, "kind"),
                        nullableExact(item, "task_id"), exact(item, "timestamp")));
            }
            return new EventPage(exact(json, "project_id"), List.copyOf(events), nullableExact(json, "next_event_id"), json.getBoolean("has_more"));
        } catch (JSONException invalid) {
            throw malformed(invalid);
        }
    }

    @Override public ApprovalDecision approve(String projectId, String approvalId, boolean approved, String response) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("approved", approved);
            body.put("response", response == null ? JSONObject.NULL : response);
            JSONObject json = request("POST", "/v1/projects/" + segment(projectId) + "/approvals/" + segment(approvalId), body);
            return new ApprovalDecision(exact(json, "project_id"), exact(json, "approval_id"), json.getBoolean("approved"), nullableExact(json, "response"));
        } catch (JSONException invalid) {
            throw malformed(invalid);
        }
    }

    @Override public Cancellation cancel(String projectId) throws IOException {
        JSONObject json = request("POST", "/v1/projects/" + segment(projectId) + "/cancel", new JSONObject());
        try { return new Cancellation(exact(json, "project_id"), exact(json, "state")); }
        catch (JSONException invalid) { throw malformed(invalid); }
    }

    @Override public GoalResult result(String projectId) throws IOException {
        JSONObject json = request("GET", "/v1/projects/" + segment(projectId) + "/result", null);
        try {
            rejectProviderLeak(json);
            return new GoalResult(exact(json, "project_id"), exact(json, "state"), exact(json, "result"));
        } catch (JSONException invalid) {
            throw malformed(invalid);
        }
    }

    private JSONObject request(String method, String path, JSONObject body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String payload = read(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new IOException("JARVIS management request failed with HTTP " + status);
        }
        try { return payload.isBlank() ? new JSONObject() : new JSONObject(payload); }
        catch (JSONException invalid) { throw malformed(invalid); }
    }

    private static void rejectProviderLeak(JSONObject json) throws IOException, JSONException {
        if (json.has(PROVIDER_GUARD) && json.getBoolean(PROVIDER_GUARD)) throw new IOException(PROVIDER_LEAK);
    }

    private static JSONArray strings(List<String> values) {
        JSONArray out = new JSONArray();
        if (values != null) for (String value : values) if (value != null) out.put(value);
        return out;
    }

    private static String exact(JSONObject json, String key) throws JSONException { return json.getString(key); }
    private static String nullableExact(JSONObject json, String key) throws JSONException {
        return !json.has(key) || json.isNull(key) ? null : json.getString(key);
    }
    private static String segment(String value) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("JARVIS identifier is required");
        return Uri.encode(value);
    }
    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) out.append(line);
        }
        return out.toString();
    }
    private static IOException malformed(Exception cause) { return new IOException("Malformed JARVIS management response", cause); }

    private static void validateTransport(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) throw new IllegalArgumentException("JARVIS base URL must be absolute");
            boolean loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
            if (!"https".equalsIgnoreCase(scheme) && !(loopback && "http".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Remote JARVIS connections require HTTPS");
            }
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        }
    }
}
