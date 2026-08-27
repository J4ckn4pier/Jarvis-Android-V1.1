package com.jarvis.mobile.brain.providers;

import com.jarvis.mobile.brain.core.IntentPlan;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ProviderSchema {
    private ProviderSchema() {}

    public static String systemPrompt() {
        return "You are the language cortex for JARVIS, a concise, capable executive assistant. " +
                "Address the owner as sir when natural. Return only a schema-valid proposal. " +
                "Never claim an action happened and never invent arbitrary commands. The Android policy layer executes approved actions.";
    }

    public static JSONObject jsonSchema() {
        JSONArray intents = new JSONArray();
        for (IntentPlan.Intent value : IntentPlan.Intent.values()) intents.put(value.name());
        JSONObject props = new JSONObject()
                .put("kind", new JSONObject().put("type", "string").put("enum", new JSONArray().put("plan")))
                .put("intent", new JSONObject().put("type", "string").put("enum", intents))
                .put("payload", new JSONObject().put("type", "string"))
                .put("answer", new JSONObject().put("type", "string"))
                .put("cue", new JSONObject().put("type", "string"))
                .put("confidence", new JSONObject().put("type", "number").put("minimum", 0).put("maximum", 1));
        return new JSONObject().put("type", "object").put("additionalProperties", false)
                .put("properties", props)
                .put("required", new JSONArray(ListHolder.FIELDS));
    }

    private static final class ListHolder {
        static final String[] FIELDS = {"kind", "intent", "payload", "answer", "cue", "confidence"};
    }
}
