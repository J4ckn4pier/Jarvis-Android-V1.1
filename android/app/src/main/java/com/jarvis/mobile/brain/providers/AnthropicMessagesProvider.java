package com.jarvis.mobile.brain.providers;

import com.jarvis.mobile.brain.core.IntentPlan;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;

public final class AnthropicMessagesProvider implements CortexProvider {
    private final String endpoint, model, apiKey;
    public AnthropicMessagesProvider(String endpoint, String model, String apiKey) {
        this.endpoint = endpoint == null || endpoint.isBlank() ? "https://api.anthropic.com/v1/messages" : endpoint.trim();
        this.model = clean(model); this.apiKey = clean(apiKey);
    }
    public String id() { return "anthropic_messages"; }
    public boolean isConfigured() { return !model.isEmpty() && !apiKey.isEmpty(); }
    public IntentPlan propose(String utterance) throws Exception {
        if (!isConfigured()) return IntentPlan.unknown();
        JSONObject tool = new JSONObject().put("name", "jarvis_plan")
                .put("description", "Propose one schema-bounded JARVIS intent; never execute it.")
                .put("input_schema", ProviderSchema.jsonSchema()).put("strict", true);
        JSONObject body = new JSONObject().put("model", model).put("max_tokens", 500)
                .put("system", ProviderSchema.systemPrompt())
                .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", utterance)))
                .put("tools", new JSONArray().put(tool))
                .put("tool_choice", new JSONObject().put("type", "tool").put("name", "jarvis_plan"));
        JSONObject response = HttpJson.post(endpoint, Map.of("x-api-key", apiKey, "anthropic-version", "2023-06-01"), body);
        JSONArray content = response.optJSONArray("content");
        if (content != null) for (int i = 0; i < content.length(); i++) {
            JSONObject item = content.optJSONObject(i);
            if (item != null && "tool_use".equals(item.optString("type")) && "jarvis_plan".equals(item.optString("name")))
                return ProviderPlanFactory.fromJson(item.optJSONObject("input"));
        }
        return IntentPlan.unknown();
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
