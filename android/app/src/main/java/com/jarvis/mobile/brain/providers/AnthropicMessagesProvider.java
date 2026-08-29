package com.jarvis.mobile.brain.providers;

import com.jarvis.brain.ReasoningRequest;
import com.jarvis.brain.ReasoningResult;
import com.jarvis.brain.ToolRegistry;
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

    /** Legacy compatibility path for callers not yet migrated to shared typed plans. */
    public IntentPlan propose(String utterance) throws Exception {
        if (!isConfigured()) return IntentPlan.unknown();
        JSONObject input = toolInput(utterance, ProviderSchema.systemPrompt(), ProviderSchema.jsonSchema(), "jarvis_plan");
        return input == null ? IntentPlan.unknown() : ProviderPlanFactory.fromJson(input);
    }

    @Override
    public ReasoningResult proposeReasoning(ReasoningRequest request, ToolRegistry tools) throws Exception {
        if (!isConfigured()) return new ReasoningResult(id(), "", null);
        JSONObject input = toolInput(request.utterance(), ProviderSharedPlanSchema.systemPrompt(),
                ProviderSharedPlanSchema.jsonSchema(tools), "jarvis_shared_plan");
        return input == null
                ? new ReasoningResult(id(), "", null)
                : ProviderSharedPlanFactory.fromJson(id(), input, tools);
    }

    private JSONObject toolInput(String utterance, String systemPrompt, JSONObject schema, String toolName) throws Exception {
        JSONObject tool = new JSONObject().put("name", toolName)
                .put("description", "Propose a schema-bounded JARVIS reasoning result; never execute it.")
                .put("input_schema", schema).put("strict", true);
        JSONObject body = new JSONObject().put("model", model).put("max_tokens", 900)
                .put("system", systemPrompt)
                .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", utterance)))
                .put("tools", new JSONArray().put(tool))
                .put("tool_choice", new JSONObject().put("type", "tool").put("name", toolName));
        JSONObject response = HttpJson.post(endpoint, Map.of("x-api-key", apiKey, "anthropic-version", "2023-06-01"), body);
        JSONArray content = response.optJSONArray("content");
        if (content != null) for (int i = 0; i < content.length(); i++) {
            JSONObject item = content.optJSONObject(i);
            if (item != null && "tool_use".equals(item.optString("type")) && toolName.equals(item.optString("name")))
                return item.optJSONObject("input");
        }
        return null;
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
