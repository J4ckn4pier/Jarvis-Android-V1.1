package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Static contract proving Android persists settings and applies profile/personality to user-facing behavior. */
public final class AndroidSettingsPersistenceContractTest {
    public static void main(String[] args) throws Exception {
        Path adapterPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidSharedPreferencesSettingsPersistence.java");
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        Path preferenceContextPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidUserPreferenceContextSource.java");
        Path stylePath = Path.of("src/main/java/com/jarvis/brain/ResponseStyleContract.java");
        check(Files.exists(adapterPath), "Android must bind a private persistence adapter for non-secret brain settings");
        String adapter = Files.readString(adapterPath);
        String lower = adapter.toLowerCase();
        String runtime = Files.readString(runtimePath);

        check(adapter.contains("implements SettingsPersistence"), "Android settings adapter must implement the shared persistence port");
        check(adapter.contains("MODE_PRIVATE"), "Android settings persistence must use app-private preferences");
        check(adapter.contains("jarvis_brain_settings"), "brain settings need a dedicated preference namespace");
        check(!adapter.contains("import com.jarvis.mobile.brain.providers.SecureSecretStore")
                        && !adapter.contains("new SecureSecretStore(")
                        && !lower.contains("api_key")
                        && !lower.contains("openai_api_key")
                        && !lower.contains("anthropic_api_key")
                        && !lower.contains("access_token")
                        && !lower.contains("refresh_token"),
                "generic settings persistence must not bind provider credential storage");
        check(runtime.contains("new AndroidSharedPreferencesSettingsPersistence(app)"),
                "Android runtime must compose the persistent non-secret SettingsStore adapter");
        check(runtime.contains("new SettingsStore("), "Android runtime must construct SettingsStore from persistence");

        check(Files.exists(preferenceContextPath),
                "Profile and Personality controls must have a production reasoning-context binding, not persistence-only UI");
        String preferenceContext = Files.readString(preferenceContextPath);
        check(preferenceContext.contains("profile_name") && preferenceContext.contains("personality_label"),
                "reasoning context must consume the exact user-facing Profile and Personality preferences");
        check(runtime.contains("new AndroidUserPreferenceContextSource(app)"),
                "Android runtime must include user Profile and Personality in assistant reasoning context");
        String style = Files.readString(stylePath);
        check(style.contains("user-selected personality") && style.contains("form of address"),
                "provider response style must explicitly honor user-selected personality and address preferences");
        check(runtime.contains("private String preferredAddress()"),
                "deterministic Android runtime replies must use the saved Profile form of address too");
        check(runtime.contains("getSharedPreferences(\"jarvis_shell\"") && runtime.contains("profile_name"),
                "deterministic reply address must come from the same Profile setting shown to the user");
        check(!runtime.contains("needs your approval, sir")
                        && !runtime.contains("working on that, sir")
                        && !runtime.contains("background task, sir")
                        && !runtime.contains("Cancelled, sir")
                        && !runtime.contains("Certainly, sir"),
                "background-project replies must not bypass the user-selected Profile with hardcoded sir text");

        System.out.println("AndroidSettingsPersistenceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
