package com.jarvis.mobile.brain.providers;

import android.content.Context;
import android.content.SharedPreferences;

public final class CortexProviderFactory {
    public static final String MODE_LOCAL = "local";
    public static final String MODE_OPENAI = "openai";
    public static final String MODE_ANTHROPIC = "anthropic";
    private CortexProviderFactory() {}
    public static CortexProvider create(Context context) {
        SharedPreferences p = context.getSharedPreferences("jarvis_cortex", Context.MODE_PRIVATE);
        String mode = p.getString("mode", p.getString("provider", MODE_LOCAL));
        String endpoint = p.getString("endpoint", ""); String model = p.getString("model", "");
        String key = new SecureSecretStore(context).get("provider_api_key");
        if (MODE_OPENAI.equals(mode)) return new OpenAIResponsesProvider(endpoint, model, key);
        if (MODE_ANTHROPIC.equals(mode)) return new AnthropicMessagesProvider(endpoint, model, key);
        return new CortexProvider() {
            public String id() { return "local"; }
            public boolean isConfigured() { return true; }
            public com.jarvis.mobile.brain.core.IntentPlan propose(String utterance) { return com.jarvis.mobile.brain.core.IntentPlan.unknown(); }
        };
    }
    public static String status(Context context) {
        CortexProvider provider = create(context);
        if ("local".equals(provider.id())) return "Local reasoning (offline)";
        return provider.isConfigured() ? provider.id() + " configured"
                : provider.id() + " needs a model and API key";
    }
}
