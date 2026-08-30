package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** The native UI/backend settings surfaces must configure the exact research gateway used by the live Android brain. */
public final class AndroidResearchSettingsCompositionContractTest {
    public static void main(String[] args) throws Exception {
        String settings = Files.readString(Path.of("src/main/java/com/jarvis/brain/SettingsStore.java"));
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));
        String adapter = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidExternalResearchGateway.java"));
        String activity = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"));

        check(settings.contains("RESEARCH_ENDPOINT") && settings.contains("research_endpoint"),
                "shared settings must expose a stable non-secret research endpoint key");
        check(runtime.indexOf("settings = new SettingsStore") < runtime.indexOf("AndroidExternalResearchGateway.create(app, settings)"),
                "Android runtime must construct shared settings before research and inject the same store into the gateway");
        check(runtime.contains("AndroidExternalResearchGateway.create(app, settings)"),
                "live research gateway must consume the same SettingsStore exposed by JarvisUiBackend");
        check(adapter.contains("SettingsStore settings") && adapter.contains("settings.get(SettingsStore.RESEARCH_ENDPOINT)"),
                "research adapter must read endpoint configuration dynamically from shared backend settings");
        check(!adapter.contains("getSharedPreferences(PREFS"),
                "research endpoint must not live in a hidden duplicate preferences silo outside the UI/backend state");
        check(activity.contains("new SettingsStore(new AndroidSharedPreferencesSettingsPersistence(this))"),
                "native Android Settings must use the same persisted brain-settings store as the live research gateway");
        check(activity.contains("SettingsStore.RESEARCH_ENDPOINT")
                        && activity.contains("researchEndpoint.getText().toString().trim()")
                        && activity.contains("researchSettings.put("),
                "native Android Settings must expose and persist the research endpoint instead of leaving fresh research unreachable from the phone UI");
        check(activity.contains("Research endpoint") && activity.contains(".local"),
                "native research endpoint guidance must explain the safe user-owned local option");

        System.out.println("AndroidResearchSettingsCompositionContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
