package com.jarvis.mobile.brain.providers;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class HttpJson {
    private static final int MAX = 1_048_576;
    private HttpJson() {}

    static JSONObject post(String endpoint, Map<String, String> headers, JSONObject body) throws Exception {
        URI uri = URI.create(endpoint);
        String host = uri.getHost() == null ? "" : uri.getHost();
        boolean loopback = host.equals("127.0.0.1") || host.equals("localhost") || host.equals("10.0.2.2");
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !loopback) throw new IllegalArgumentException("HTTPS is required");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("POST"); connection.setConnectTimeout(12_000); connection.setReadTimeout(45_000);
        connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
        for (Map.Entry<String, String> h : headers.entrySet()) connection.setRequestProperty(h.getKey(), h.getValue());
        try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = connection.getResponseCode();
        InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = read(input);
        if (code < 200 || code >= 300) throw new IllegalStateException("Provider HTTP " + code + ": " + text);
        return new JSONObject(text);
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int total = 0, n;
        while ((n = input.read(buffer)) >= 0) { total += n; if (total > MAX) throw new IllegalStateException("Provider response too large"); out.write(buffer, 0, n); }
        return out.toString(StandardCharsets.UTF_8);
    }
}
