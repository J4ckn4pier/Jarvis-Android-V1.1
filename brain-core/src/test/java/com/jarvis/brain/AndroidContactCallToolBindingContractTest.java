package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Ordinary contact calls must use a typed, exact-target, approval-gated Android path. */
public final class AndroidContactCallToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        ToolRegistry.RegisteredTool call = ToolRegistry.standard().resolve("call_contact").orElseThrow();
        check(call.spec().consequential(), "placing a contact call must require approval");
        check(call.spec().executionClass() == ToolExecutionClass.CONSEQUENTIAL,
                "contact calls must remain a consequential execution class");
        check(call.spec().requiredArguments().equals(Set.of("recipient")),
                "contact call must require an explicit recipient");

        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        String dialer = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidDialerActions.java"));
        String brain = Files.readString(Path.of("src/main/java/com/jarvis/brain/BrainEngine.java"));

        check(factory.contains("args -> dialer.call(args.get(\"recipient\"))"),
                "Android production registry must bind call_contact to the typed dialer adapter");
        check(dialer.contains("Intent.ACTION_CALL"),
                "approved contact calling must use Android ACTION_CALL rather than pretending ACTION_DIAL placed a call");
        check(dialer.contains("UniqueNamedTargetResolver.resolve"),
                "named contact calls must use the shared exact-unique resolver instead of first partial match");
        check(dialer.contains("Manifest.permission.CALL_PHONE") && dialer.contains("Manifest.permission.READ_CONTACTS"),
                "contact calling must fail closed when call or contact permissions are unavailable");
        check(brain.contains("\"call_contact\"") && brain.contains("Map.of(\"recipient\""),
                "natural call-contact requests must route into the typed contact call tool");

        System.out.println("AndroidContactCallToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
