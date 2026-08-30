package com.jarvis.mobile.brain.providers;

import android.content.Context;
import android.content.SharedPreferences;
import com.jarvis.brain.ReasoningRequest;
import com.jarvis.brain.ReasoningResult;
import com.jarvis.brain.ToolRegistry;

public final class CortexProviderFactory {
    public static final String MODE_LOCAL = "local";
    public static final String MODE_OPENAI_COMPATIBLE = "openai_compatible";
    public static final String MODE_OPENAI = "openai";
    public static final String MODE_ANTHROPIC = "anthropic";
    private CortexProviderFactory() {}

    public static CortexProvider create(Context context) {
        SharedPreferences p = context.getSharedPreferences("jarvis_cortex", Context.MODE_PRIVATE);
        String mode = p.getString("mode", p.getString("provider", MODE_LOCAL));
        String endpoint = p.getString("endpoint", "");
        String model = p.getString("model", "");
        String key = new SecureSecretStore(context).get("provider_api_key");
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
        CortexProvider provider = create(context);
        if ("local".equals(provider.id())) return "Deterministic brain active; no general local cortex configured";
        if (MODE_OPENAI_COMPATIBLE.equals(provider.id())) {
            return provider.isConfigured() ? "OpenAI-compatible local cortex configured"
                    : "OpenAI-compatible local cortex needs a model and allowed endpoint";
        }
        return provider.isConfigured() ? provider.id() + " configured"
                : provider.id() + " needs a model and API key";
    }
}
