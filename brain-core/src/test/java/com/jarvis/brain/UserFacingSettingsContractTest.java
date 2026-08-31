package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

public final class UserFacingSettingsContractTest {
    public static void main(String[] args) throws Exception {
        Path mobile = Path.of("../android/app/src/main/java/com/jarvis/mobile");
        Path providers = mobile.resolve("brain/providers");
        String settings = Files.readString(mobile.resolve("SettingsActivity.java"));
        String voiceSession = Files.readString(mobile.resolve("assistant/JarvisVoiceSession.java"));
        String quickWidget = Files.readString(mobile.resolve("widgets/QuickActivationWidget.java"));
        String providerFactory = Files.readString(providers.resolve("CortexProviderFactory.java"));
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
        check(!settings.contains("PREFRONTAL CORTEX"), "normal Settings must not expose internal cortex jargon");
        check(!settings.contains("API key"), "normal Settings must not expose a raw API-key field");
        check(!settings.contains("RESEARCH ENDPOINT"), "normal Settings must not expose raw research endpoint controls");
        check(!settings.contains("127.0.0.1"), "normal Settings must not expose backend endpoint examples");
        check(Files.exists(mobile.resolve("DeveloperSettingsActivity.java")), "raw provider configuration must be preserved behind an advanced screen");
        check(manifest.contains(".DeveloperSettingsActivity"), "developer settings must remain declared rather than deleted");
        check(manifest.contains(".SettingsActivity") && manifest.contains("@style/AppTheme"), "user Settings must use the canonical dark JARVIS theme");

        check(settings.contains("lock_screen_assistant_enabled"),
                "Widgets & Lock Screen must persist the user-visible lock-screen assistant preference");
        check(voiceSession.contains("lock_screen_assistant_enabled") && voiceSession.contains("KeyguardManager"),
                "the real assistant session must enforce the saved lock-screen preference against Android keyguard state");
        check(voiceSession.contains("lockScreenAssistantAllowed()"),
                "lock-screen access enforcement must be explicit and reviewable in the production voice session");
        check(manifest.contains(".widgets.QuickActivationWidget") && quickWidget.contains("AppWidgetProvider"),
                "Widgets & Lock Screen may only advertise widget setup when a real Android home-screen widget exists");
        check(settings.contains("requestPinAppWidget") && settings.contains("QuickActivationWidget.class"),
                "Widgets & Lock Screen must expose a real Android action to add the working JARVIS Quick Access widget");

        check(providerFactory.contains("getSharedPreferences(\"jarvis_shell\"") && providerFactory.contains("personality_label"),
                "the selected Personality must be read by the production cortex factory, not only saved by Settings");
        check(providerSchema.contains("personalityDirective"),
                "provider system prompts must support the selected JARVIS personality as a response-style directive");
        check(openAiCompatible.contains("systemPrompt(personalityDirective)"),
                "free/local OpenAI-compatible cortex must receive the selected Personality");
        check(openAi.contains("systemPrompt(personalityDirective)"),
                "OpenAI cortex must receive the selected Personality");
        check(anthropic.contains("systemPrompt(personalityDirective)"),
                "Anthropic cortex must receive the selected Personality");

        System.out.println("UserFacingSettingsContractTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
