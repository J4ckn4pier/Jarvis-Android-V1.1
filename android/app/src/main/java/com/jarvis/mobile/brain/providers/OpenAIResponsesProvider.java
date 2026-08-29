package com.jarvis.mobile.brain.providers;

import com.jarvis.brain.ReasoningRequest;
import com.jarvis.brain.ReasoningResult;
import com.jarvis.brain.ToolRegistry;
import com.jarvis.mobile.brain.core.IntentPlan;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;

public final class OpenAIResponsesProvider implements CortexProvider {
    private final String endpoint, model, apiKey;
    public OpenAIResponsesProvider(String endpoint, String model, String apiKey) {
        this.endpoint = endpoint == null || endpoint.isBlank() ? "https://api.openai.com/v1/responses" : endpoint.trim();
        this.model = clean(model); this.apiKey = clean(apiKey);
    }
    public String id() { return "openai_responses"; }
    public boolean isConfigured() { return !model.isEmpty() && !apiKey.isEmpty(); }

    /** Legacy compatibility path for callers not yet migrated to shared typed plans. */
    public IntentPlan propose(String utterance) throws Exception {
        if (!isConfigured()) return IntentPlan.unknown();
        JSONObject format = new JSONObject().put("type", "json_schema").put("name", "jarvis_plan")
                .put("strict", true).put("schema", ProviderSchema.jsonSchema());
        JSONObject response = post(utterance, ProviderSchema.systemPrompt(), format);
        String json = outputText(response);
        return json.isEmpty() ? IntentPlan.unknown() : ProviderPlanFactory.fromJson(new JSONObject(json));
    }

    @Override
    public ReasoningResult proposeReasoning(ReasoningRequest request, ToolRegistry tools) throws Exception {
        if (!isConfigured()) return new ReasoningResult(id(), "", null);
        JSONObject format = new JSONObject().put("type", "json_schema").put("name", "jarvis_shared_plan")
                .put("strict", true).put("schema", ProviderSharedPlanSchema.jsonSchema(tools));
        JSONObject response = post(request.utterance(), ProviderSharedPlanSchema.systemPrompt(), format);
        String json = outputText(response);
        return json.isEmpty()
                ? new ReasoningResult(id(), "", null)
                : ProviderSharedPlanFactory.fromJson(id(), new JSONObject(json), tools);
    }

    private JSONObject post(String utterance, String systemPrompt, JSONObject format) throws Exception {
        JSONArray input = new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", systemPrompt))
                .put(new JSONObject().put("role", "user").put("content", utterance));
        JSONObject body = new JSONObject().put("model", model).put("store", false).put("input", input)
                .put("text", new JSONObject().put("format", format));
        return HttpJson.post(endpoint, Map.of("Authorization", "Bearer " + apiKey), body);
    }

    private static String outputText(JSONObject response) {
        String json = response.optString("output_text", "");
        if (!json.isEmpty()) return json;
        JSONArray output = response.optJSONArray("output");
        if (output != null) outer: for (int i = 0; i < output.length(); i++) {
            JSONObject message = output.optJSONObject(i);
            if (message == null) continue;
            JSONArray content = message.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject item = content.optJSONObject(j);
                if (item != null && "output_text".equals(item.optString("type"))) {
                    json = item.optString("text");
                    break outer;
                }
            }
        }
        return json;
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
