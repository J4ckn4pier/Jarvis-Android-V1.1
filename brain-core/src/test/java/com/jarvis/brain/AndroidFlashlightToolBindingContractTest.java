package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** set_flashlight(state) must preserve typed state instead of routing through free-form command text. */
public final class AndroidFlashlightToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        Path flashlightPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidFlashlightActions.java");
        check(Files.exists(flashlightPath), "Android production must provide a typed flashlight action adapter");
        String flashlight = Files.readString(flashlightPath);

        check(factory.contains("AndroidFlashlightActions flashlight = new AndroidFlashlightActions(appContext)"),
                "Android tool registry must compose the typed flashlight adapter");
        check(factory.contains("args -> flashlight.setState(args.get(\"state\"))"),
                "set_flashlight must pass the structured state directly to the Android adapter");
        check(!factory.contains("actions.execute(\"flashlight \" + args.get(\"state\"))"),
                "set_flashlight must not flatten state into the legacy string parser");
        check(flashlight.contains("CameraManager"), "flashlight action must use Android CameraManager");
        check(flashlight.contains("setTorchMode"), "flashlight action must call setTorchMode");
        check(flashlight.contains("Manifest.permission.CAMERA"), "flashlight action must respect camera permission");
        check(flashlight.contains("FLASH_INFO_AVAILABLE"), "flashlight action must select a flash-capable camera");
        check(flashlight.contains("LENS_FACING_BACK"), "flashlight action should prefer a back-facing camera");

        System.out.println("AndroidFlashlightToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
