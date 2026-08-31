package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

public final class UserFacingSettingsContractTest {
    public static void main(String[] args) throws Exception {
        Path mobile = Path.of("../android/app/src/main/java/com/jarvis/mobile");
        Path providers = mobile.resolve("brain/providers");
        String settings = Files.readString(mobile.resolve("SettingsActivity.java"));
        String developerSettings = Files.readString(mobile.resolve("DeveloperSettingsActivity.java"));
        String voiceSession = Files.readString(mobile.resolve("assistant/JarvisVoiceSession.java"));
        String quickWidget = Files.readString(mobile.resolve("widgets/QuickActivationWidget.java"));
        String webSearch = Files.readString(mobile.resolve("actions/AndroidWebSearchActions.java"));
        String defaultAppPersistence = Files.readString(mobile.resolve("brain/AndroidDefaultAppPreferencePersistence.java"));
        String providerFactory = Files.readString(providers.resolve("CortexProviderFactory.java"));
        String localAiPolicy = Files.readString(providers.resolve("LocalAiEndpointPolicy.java"));
        String providerSchema = Files.readString(providers.resolve("ProviderSharedPlanSchema.java"));
        String openAiCompatible = Files.readString(providers.resolve("OpenAiCompatibleChatProvider.java"));
        String openAi = Files.readString(providers.resolve("OpenAIResponsesProvider.java"));
        String anthropic = Files.readString(providers.resolve("AnthropicMessagesProvider.java"));
        String manifest = Files.readString(Path.of("../android/app/src/main/AndroidManifest.xml"));
        for (String title : new String[]{"Voice", "Wake Word", "Voice Model", "Language", "App Permissions", "AI Providers", "Backup & Sync", "Profile", "Default Apps", "Personality", "Widgets & Lock Screen"}) {
            check(settings.contains(title), "user Settings must include canonical group: " + title);
        }
        check(settings.contains("showProviderConnections"),
                "AI Providers must open a real user-facing connection surface instead of a status-only toast");
        check(settings.contains("CONNECT / CHANGE") && settings.contains("DISCONNECT"),
                "normal AI Providers must offer connect/change and disconnect actions");
        check(settings.contains("CortexProviderFactory.MODE_LOCAL") && settings.contains("provider_api_key"),
                "disconnect must return JARVIS to private local mode and remove the saved external credential");
        check(settings.contains("DeveloperSettingsActivity.class"),
                "connect/change may enter the explicitly advanced provider setup surface without exposing raw fields inline");
        check(providerFactory.contains("isLocalCompatibleEndpoint"),
                "provider status must classify a compatible endpoint before normal Settings calls it free/local AI");
        check(providerFactory.contains("endsWith(\".local\")") && providerFactory.contains("localhost"),
                "local compatible classification must be limited to local hostnames or loopback rather than arbitrary HTTPS providers");
        check(localAiPolicy.contains("EndpointTransportPolicy.allows")
                        && localAiPolicy.contains("endsWith(\".local\")")
                        && localAiPolicy.contains("localhost"),
                "normal Free Local AI setup must require both safe transport and a genuinely local/self-hosted hostname instead of accepting an arbitrary cloud HTTPS endpoint");
        check(settings.contains("Use a local .local hostname, localhost, or loopback address for Free Local AI."),
                "Free Local AI validation errors must describe the endpoints the local-only policy actually accepts");
        check(!settings.contains("Use HTTPS or a local .local JARVIS/Ollama endpoint."),
                "Free Local AI must not tell users arbitrary HTTPS endpoints are accepted when the local-only policy rejects them");
        check(developerSettings.contains("EndpointTransportPolicy.allows"),
                "Developer Options must apply the shared safe endpoint transport policy before saving provider endpoints");
        check(!settings.contains("PREFRONTAL CORTEX"), "normal Settings must not expose internal cortex jargon");
        check(!settings.contains("API key"), "normal Settings must not expose a raw API-key field");
        check(!settings.contains("RESEARCH ENDPOINT"), "normal Settings must not expose raw research endpoint controls");
        check(!settings.contains("127.0.0.1"), "normal Settings must not expose backend endpoint examples");
        check(Files.exists(mobile.resolve("DeveloperSettingsActivity.java")), "raw provider configuration must be preserved behind an advanced screen");
        check(manifest.contains(".DeveloperSettingsActivity"), "developer settings must remain declared rather than deleted");
        check(manifest.contains(".SettingsActivity") && manifest.contains("@style/AppTheme"), "user Settings must use the canonical dark JARVIS theme");

        check(settings.contains("lock_screen_assistant_enabled"),
                "Widgets & Lock Screen must persist the user-visible lock-screen assistant preference");
        check(settings.contains("boolean[] pendingLockScreen")
                        && settings.contains("setPositiveButton(\"SAVE\"")
                        && settings.contains("setNegativeButton(\"CANCEL\",null)"),
                "Widgets & Lock Screen must keep lock-screen edits provisional until SAVE and allow CANCEL without mutating runtime state");
        check(voiceSession.contains("lock_screen_assistant_enabled") && voiceSession.contains("KeyguardManager"),
                "the real assistant session must enforce the saved lock-screen preference against Android keyguard state");
        check(voiceSession.contains("lockScreenAssistantAllowed()"),
                "lock-screen access enforcement must be explicit and reviewable in the production voice session");
        check(manifest.contains(".widgets.QuickActivationWidget") && quickWidget.contains("AppWidgetProvider"),
                "Widgets & Lock Screen may only advertise widget setup when a real Android home-screen widget exists");
        check(settings.contains("requestPinAppWidget") && settings.contains("QuickActivationWidget.class"),
                "Widgets & Lock Screen must expose a real Android action to add the working JARVIS Quick Access widget");

        check(defaultAppPersistence.contains("jarvis_default_apps"),
                "JARVIS must preserve an app-private default-app preference store rather than relying only on Android system defaults");
        check(settings.contains("showDefaultAppSettings") && settings.contains("AndroidDefaultAppPreferencePersistence"),
                "Default Apps must expose a real JARVIS preference editor instead of only opening Android's generic default-app screen");
        check(settings.contains("browser") && settings.contains("ACTION_VIEW"),
                "Default Apps must let the user choose a browser that can actually handle web links");
        check(webSearch.contains("AndroidDefaultAppPreferencePersistence") && webSearch.contains("preferredBrowserPackage"),
                "the production web-search action must read the saved JARVIS browser preference");
        check(webSearch.contains("setPackage(preferredBrowserPackage)"),
                "the saved JARVIS browser preference must affect the actual Android browser intent");

        check(providerFactory.contains("getSharedPreferences(\"jarvis_shell\"") && providerFactory.contains("personality_label"),
                "the selected Personality must be read by the production cortex factory, not only saved by Settings");
        check(providerFactory.contains("ProviderSharedPlanSchema.setPersonalityLabel"),
                "the production cortex factory must hydrate the selected Personality into the provider-neutral prompt boundary");
        check(providerSchema.contains("setPersonalityLabel") && providerSchema.contains("personalityDirectiveFor"),
                "provider system prompts must translate the selected JARVIS personality into a bounded response-style directive");
        check(openAiCompatible.contains("ProviderSharedPlanSchema.systemPrompt()"),
                "free/local OpenAI-compatible cortex must use the provider-neutral Personality-aware system prompt");
        check(openAi.contains("ProviderSharedPlanSchema.systemPrompt()"),
                "OpenAI cortex must use the provider-neutral Personality-aware system prompt");
        check(anthropic.contains("ProviderSharedPlanSchema.systemPrompt()"),
                "Anthropic cortex must use the provider-neutral Personality-aware system prompt");

        check(providerFactory.contains("profile_name") && providerFactory.contains("ProviderSharedPlanSchema.setProfileName"),
                "the saved Profile name must be hydrated into the production provider-neutral prompt boundary instead of remaining display-only");
        check(providerSchema.contains("setProfileName") && providerSchema.contains("USER ADDRESS"),
                "provider system prompts must carry the saved Profile name as bounded address context");

        System.out.println("UserFacingSettingsContractTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
