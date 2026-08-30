package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** volume_control(action) must use a typed Android audio adapter and fail closed on unknown actions. */
public final class AndroidVolumeToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String registry = Files.readString(Path.of("src/main/java/com/jarvis/brain/ToolRegistry.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        Path volumePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidVolumeActions.java");
        check(registry.contains("r.register(spec(\"volume_control\""), "shared brain registry must expose volume_control");
        check(Files.exists(volumePath), "Android production must provide a typed volume adapter");
        String volume = Files.readString(volumePath);
        check(factory.contains("args -> volume.control(args.get(\"action\"))"), "Android registry must bind typed volume control");
        check(volume.contains("AudioManager.ADJUST_RAISE") && volume.contains("AudioManager.ADJUST_LOWER"), "volume adapter must support louder and quieter");
        check(volume.contains("AudioManager.ADJUST_MUTE") && volume.contains("AudioManager.ADJUST_UNMUTE"), "volume adapter must support mute and unmute");
        check(volume.contains("Unsupported volume action"), "unknown volume actions must fail closed");
        System.out.println("AndroidVolumeToolBindingContractTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
