package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** open_app(app) must use an exact typed Android app resolver and never launch the first partial label match. */
public final class AndroidAppLaunchToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String registry = Files.readString(Path.of("src/main/java/com/jarvis/brain/ToolRegistry.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        Path actionPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidAppActions.java");
        check(registry.contains("r.register(spec(\"open_app\""), "shared brain registry must expose open_app");
        check(registry.contains("Set.of(\"app\")"), "open_app must require an explicit app argument");
        check(Files.exists(actionPath), "Android production must provide a typed app-launch adapter");
        String action = Files.readString(actionPath);
        check(factory.contains("args -> apps.open(args.get(\"app\"))"), "Android registry must bind open_app to the typed adapter");
        check(action.contains("label.equalsIgnoreCase(target)"), "app launch must require an exact visible label match");
        check(!action.contains("label.toLowerCase") || !action.contains("contains(lower)"), "app launch must not choose a partial label match");
        check(action.contains("getLaunchIntentForPackage"), "typed app launch must use the resolved package's launch intent");
        check(action.contains("I couldn’t find an installed app named"), "unknown apps must fail closed");
        System.out.println("AndroidAppLaunchToolBindingContractTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
