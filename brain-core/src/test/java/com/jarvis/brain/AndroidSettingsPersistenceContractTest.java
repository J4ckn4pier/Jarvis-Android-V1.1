package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Static contract proving Android persists non-secret brain settings without absorbing credentials. */
public final class AndroidSettingsPersistenceContractTest {
    public static void main(String[] args) throws Exception {
        Path adapterPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidSharedPreferencesSettingsPersistence.java");
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        check(Files.exists(adapterPath), "Android must bind a private persistence adapter for non-secret brain settings");
        String adapter = Files.readString(adapterPath);
        String runtime = Files.readString(runtimePath);

        check(adapter.contains("implements SettingsPersistence"), "Android settings adapter must implement the shared persistence port");
        check(adapter.contains("MODE_PRIVATE"), "Android settings persistence must use app-private preferences");
        check(adapter.contains("jarvis_brain_settings"), "brain settings need a dedicated preference namespace");
        check(!adapter.toLowerCase().contains("api_key") && !adapter.toLowerCase().contains("token")
                        && !adapter.toLowerCase().contains("secret"),
                "generic settings persistence must not know provider credentials or secret material");
        check(runtime.contains("new AndroidSharedPreferencesSettingsPersistence(app)"),
                "Android runtime must compose the persistent non-secret SettingsStore adapter");
        check(runtime.contains("new SettingsStore("), "Android runtime must construct SettingsStore from persistence");

        System.out.println("AndroidSettingsPersistenceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
