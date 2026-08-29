package com.jarvis.mobile.brain;

import android.content.Context;
import android.content.SharedPreferences;

import com.jarvis.brain.ExecutionContext;
import com.jarvis.brain.ExternalResearchGateway;
import com.jarvis.brain.ResearchEvidence;
import com.jarvis.brain.ToolResult;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;

/**
 * Android transport for fresh, provider-neutral external research.
 *
 * The commercial baseline intentionally ships with no public hosted research provider baked in.
 * A user/business deployment may point this adapter at an HTTPS service it is licensed to use,
 * or at a loopback/self-hosted service. Responses must carry explicit provenance so the shared
 * brain never treats an unqualified network string as fresh evidence.
 */
public final class AndroidExternalResearchGateway implements ExternalResearchGateway {
    private static final String PREFS = "jarvis_research";
    private static final String ENDPOINT_KEY = "endpoint";
    private static final int MAX_RESPONSE_BYTES = 1_048_576;

    private final String endpoint;
    private final Clock clock;

    private AndroidExternalResearchGateway(String endpoint, Clock clock) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public static ExternalResearchGateway create(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new AndroidExternalResearchGateway(preferences.getString(ENDPOINT_KEY, ""), Clock.systemUTC());
    }

    @Override
    public ToolResult discoverPlaces(Map<String, String> arguments, ExecutionContext context) {
        return research("discover_places", arguments);
    }

    @Override
    public ToolResult resolveBusiness(Map<String, String> arguments, ExecutionContext context) {
        return research("resolve_business", arguments);
    }

    @Override
    public ToolResult weatherLookup(Map<String, String> arguments, ExecutionContext context) {
        return research("weather_lookup", arguments);
    }

    @Override
    public ToolResult getMenu(Map<String, String> arguments, ExecutionContext context) {
        return research("get_menu", arguments);
    }

    @Override
    public ToolResult translate(Map<String, String> arguments, ExecutionContext context) {
        return research("translate", arguments);
    }

    @Override
    public ToolResult rankOptions(Map<String, String> arguments, ExecutionContext context) {
        return research("rank_options", arguments);
    }

    @Override
    public ToolResult presentOptions(Map<String, String> arguments, ExecutionContext context) {
        return research("present_options", arguments);
    }

    private ToolResult research(String operation, Map<String, String> arguments) {
        if (endpoint.isBlank()) return ToolResult.failure("research endpoint not configured");
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost() == null ? "" : uri.getHost();
            boolean loopback = host.equalsIgnoreCase("localhost")
                    || host.equals("127.0.0.1")
                    || host.equals("10.0.2.2");
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !loopback) {
                return ToolResult.failure("research endpoint must use HTTPS or loopback transport");
            }

            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(45_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "JARVIS-Android/1.1 research-gateway");

            JSONObject body = new JSONObject();
            body.put("operation", operation);
            body.put("arguments", new JSONObject(arguments == null ? Map.of() : arguments));
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = readBounded(input);
            if (code < 200 || code >= 300) {
                return ToolResult.failure("research endpoint HTTP " + code);
            }

            JSONObject response = new JSONObject(text);
            String payload = response.optString("payload", "").trim();
            String source = response.optString("source", "").trim();
            String observedAt = response.optString("observed_at", "").trim();
            if (payload.isBlank() || source.isBlank() || observedAt.isBlank() || !response.has("confidence")) {
                return ToolResult.failure("research endpoint response missing provenance");
            }
            double confidence = response.getDouble("confidence");
            ResearchEvidence evidence = new ResearchEvidence(payload, source, observedAt, confidence);
            return ToolResult.success(evidence.toToolOutput());
        } catch (Exception failure) {
            return ToolResult.failure("research adapter unavailable");
        }
    }

    private static String readBounded(InputStream input) throws Exception {
        if (input == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) throw new IllegalStateException("research response too large");
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
