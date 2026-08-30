package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Safe Android accessibility navigation/read controls must be reachable through typed production tools, not the retired raw router. */
public final class AndroidAccessibilityToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String registry = Files.readString(Path.of("src/main/java/com/jarvis/brain/ToolRegistry.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        Path actionPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidAccessibilityActions.java");

        check(registry.contains("r.register(spec(\"device_navigation\""),
                "shared registry must expose typed safe device navigation");
        check(registry.contains("Set.of(\"action\")"),
                "device navigation must require an explicit action");
        check(registry.contains("r.register(spec(\"screen_read\""),
                "shared registry must expose typed screen reading");
        check(Files.exists(actionPath),
                "Android production must provide a typed accessibility adapter");
        String action = Files.readString(actionPath);
        check(action.contains("JarvisAccessibilityService.back()") && action.contains("JarvisAccessibilityService.home()"),
                "typed adapter must support safe Back and Home navigation");
        check(action.contains("JarvisAccessibilityService.scrollForward()") && action.contains("JarvisAccessibilityService.scrollBackward()"),
                "typed adapter must support safe scrolling");
        check(action.contains("JarvisAccessibilityService.screenText()"),
                "typed adapter must expose current-screen text without routing through legacy parsing");
        check(action.contains("Unsupported device navigation action"),
                "unknown navigation actions must fail closed");
        check(factory.contains("AndroidAccessibilityActions accessibility = new AndroidAccessibilityActions()"),
                "Android factory must instantiate the stateless typed accessibility adapter");
        check(factory.contains("args -> accessibility.navigate(args.get(\"action\"))"),
                "Android factory must bind device_navigation to the typed adapter");
        check(factory.contains("args -> accessibility.readScreen()"),
                "Android factory must bind screen_read to the typed adapter");
        System.out.println("AndroidAccessibilityToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
