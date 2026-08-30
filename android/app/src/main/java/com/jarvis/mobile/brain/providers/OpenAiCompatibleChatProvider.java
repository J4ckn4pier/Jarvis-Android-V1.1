package com.jarvis.mobile.brain.providers;

import com.jarvis.brain.EndpointTransportPolicy;
import com.jarvis.brain.ReasoningRequest;
import com.jarvis.brain.ReasoningResult;
import com.jarvis.brain.ToolRegistry;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** OpenAI-compatible chat cortex for user-owned/local endpoints such as Ollama. */
public final class OpenAiCompatibleChatProvider implements CortexProvider {
    private static final String DEFAULT_ENDPOINT = "http://127.0.0.1:11434/v1/chat/completions";

    private final String endpoint;
    private final String model;
    private final String apiKey;

    public OpenAiCompatibleChatProvider(String endpoint, String model, String apiKey) {
        this.endpoint = normalizeEndpoint(endpoint);
        this.model = clean(model);
        this.apiKey = clean(apiKey);
    }

    @Override public String id() { return "openai_compatible"; }

    @Override public boolean isConfigured() {
        return !model.isEmpty() && EndpointTransportPolicy.allows(endpoint);
    }

    @Override
    public ReasoningResult proposeReasoning(ReasoningRequest request, ToolRegistry tools) throws Exception {
        if (!isConfigured()) return new ReasoningResult(id(), "", null);

        JSONObject jsonSchema = ProviderSharedPlanSchema.jsonSchema(tools);
        JSONObject responseFormat = new JSONObject()
                .put("type", "json_schema")
                .put("json_schema", new JSONObject()
                        .put("name", "jarvis_shared_plan")
                        .put("strict", true)
                        .put("schema", jsonSchema));

        JSONArray messages = new JSONArray()
                .put(new JSONObject()
                        .put("role", "system")
                        .put("content", ProviderSharedPlanSchema.systemPrompt()))
                .put(new JSONObject()
                        .put("role", "user")
                        .put("content", ProviderReasoningEnvelope.userContent(request)));

        JSONObject body = new JSONObject()
                .put("model", model)
                .put("stream", false)
                .put("messages", messages)
                .put("response_format", responseFormat);

        Map<String, String> headers = new HashMap<>();
        if (!apiKey.isEmpty()) headers.put("Authorization", "Bearer " + apiKey);
        JSONObject response = HttpJson.post(endpoint, headers, body);
        String json = responseText(response);
        return json.isEmpty()
                ? new ReasoningResult(id(), "", null)
                : ProviderSharedPlanFactory.fromJson(id(), new JSONObject(json), tools);
    }

    private static String responseText(JSONObject response) {
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return "";
        JSONObject first = choices.optJSONObject(0);
        if (first == null) return "";
        JSONObject message = first.optJSONObject("message");
        return message == null ? "" : message.optString("content", "").trim();
    }

    private static String normalizeEndpoint(String endpoint) {
        String value = clean(endpoint);
        if (value.isEmpty()) return DEFAULT_ENDPOINT;
        if (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.endsWith("/v1")) return value + "/chat/completions";
        return value;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
