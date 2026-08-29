package com.jarvis.mobile.brain.providers;

import com.jarvis.brain.ToolRegistry;
import com.jarvis.brain.ToolSpec;
import org.json.JSONArray;
import org.json.JSONObject;

/** Provider-facing JSON schema derived from the shared tool registry. Approval is never model-owned. */
public final class ProviderSharedPlanSchema {
    private ProviderSharedPlanSchema() {}

    public static JSONObject jsonSchema(ToolRegistry tools) {
        JSONArray toolNames = new JSONArray();
        for (ToolSpec spec : tools.specs()) toolNames.put(spec.name());

        JSONObject argument = object()
                .put("properties", new JSONObject()
                        .put("key", string())
                        .put("value", string()))
                .put("required", new JSONArray().put("key").put("value"));

        JSONObject step = object()
                .put("properties", new JSONObject()
                        .put("tool", new JSONObject().put("type", "string").put("enum", toolNames))
                        .put("arguments", new JSONObject().put("type", "array").put("items", argument)))
                .put("required", new JSONArray().put("tool").put("arguments"));

        return object()
                .put("properties", new JSONObject()
                        .put("answer", string())
                        .put("goal", string())
                        .put("steps", new JSONObject().put("type", "array").put("items", step)))
                .put("required", new JSONArray().put("answer").put("goal").put("steps"));
    }

    public static String systemPrompt() {
        return "You are a replaceable JARVIS reasoning cortex. Return only a schema-valid proposal. " +
                "Use only supplied shared tools and structured string arguments. Never decide approval, " +
                "never claim an action succeeded, and use an empty steps array for conversation-only replies.";
    }

    private static JSONObject object() {
        return new JSONObject().put("type", "object").put("additionalProperties", false);
    }

    private static JSONObject string() {
        return new JSONObject().put("type", "string");
    }
}
