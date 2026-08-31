package com.jarvis.mobile.brain.providers;

import android.content.Context;
import android.content.SharedPreferences;
import com.jarvis.brain.ReasoningRequest;
import com.jarvis.brain.ReasoningResult;
import com.jarvis.brain.ToolRegistry;

public final class CortexProviderFactory {
    public static final String MODE_LOCAL = "local";
    public static final String MODE_LOCAL_AI = "local_ai";
    public static final String MODE_OPENAI_COMPATIBLE = "openai_compatible";
    public static final String MODE_OPENAI = "openai";
    public static final String MODE_ANTHROPIC = "anthropic";
    public static final String SUGGESTED_LOCAL_MODEL = "gpt-oss:20b";
    private CortexProviderFactory() {}

    public static CortexProvider create(Context context) {
        SharedPreferences p = context.getSharedPreferences("jarvis_cortex", Context.MODE_PRIVATE);
        String mode = p.getString("mode", p.getString("provider", MODE_LOCAL));
        String endpoint = p.getString("endpoint", "");
        String model = p.getString("model", "");
        String key = new SecureSecretStore(context).get("provider_api_key");
        if (MODE_LOCAL_AI.equals(mode)) return new OpenAiCompatibleChatProvider(endpoint, model, "");
        if (MODE_OPENAI_COMPATIBLE.equals(mode)) return new OpenAiCompatibleChatProvider(endpoint, model, key);
        if (MODE_OPENAI.equals(mode)) return new OpenAIResponsesProvider(endpoint, model, key);
        if (MODE_ANTHROPIC.equals(mode)) return new AnthropicMessagesProvider(endpoint, model, key);
        return new CortexProvider() {
            public String id() { return "local"; }
            public boolean isConfigured() { return false; }
            public ReasoningResult proposeReasoning(ReasoningRequest request, ToolRegistry tools) {
                return new ReasoningResult(id(), "", null);
            }
        };
    }

    public static String status(Context context) {
        SharedPreferences p = context.getSharedPreferences("jarvis_cortex", Context.MODE_PRIVATE);
        String mode = p.getString("mode", p.getString("provider", MODE_LOCAL));
        CortexProvider provider = create(context);
        if (MODE_LOCAL.equals(mode) || "local".equals(provider.id())) {
            return "Deterministic brain active; no general local cortex configured";
        }
        if (MODE_LOCAL_AI.equals(mode)) {
            return provider.isConfigured() ? "Local AI cortex configured"
                    : "Local AI needs a model and allowed Ollama-compatible endpoint";
        }
        if (MODE_OPENAI_COMPATIBLE.equals(mode)) {
            return provider.isConfigured() ? "OpenAI-compatible cortex configured"
                    : "OpenAI-compatible cortex needs a model and allowed endpoint";
        }
        return provider.isConfigured() ? provider.id() + " configured"
                : provider.id() + " needs a model and API key";
    }
}
