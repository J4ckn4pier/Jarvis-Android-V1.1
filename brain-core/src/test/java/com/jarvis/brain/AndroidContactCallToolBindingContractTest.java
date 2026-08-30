package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

        BrainEngine brainEngine = BrainEngine.createDefault(Clock.fixed(
                Instant.parse("2026-08-30T06:00:00Z"), ZoneOffset.UTC));
        brainEngine.handle("Hey Jarvis");
        BrainResponse naturalCall = brainEngine.handle("call Mom");
        check(naturalCall.kind() == BrainResponse.Kind.ACTION_PLAN,
                "natural call-contact request must produce a typed action plan");
        PlanStep callStep = naturalCall.plan().steps().get(0);
        check(callStep.tool().equals("call_contact") && "Mom".equals(callStep.arguments().get("recipient")),
                "natural contact call must preserve the requested recipient in call_contact");
        check(naturalCall.plan().requiresApproval(),
                "natural contact call must remain blocked behind explicit approval");

        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        String dialer = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidDialerActions.java"));

        check(factory.contains("args -> dialer.call(args.get(\"recipient\"))"),
                "Android production registry must bind call_contact to the typed dialer adapter");
        check(dialer.contains("Intent.ACTION_CALL"),
                "approved contact calling must use Android ACTION_CALL rather than pretending ACTION_DIAL placed a call");
        check(dialer.contains("UniqueNamedTargetResolver.resolve"),
                "named contact calls must use the shared exact-unique resolver instead of first partial match");
        check(dialer.contains("Manifest.permission.CALL_PHONE") && dialer.contains("Manifest.permission.READ_CONTACTS"),
                "contact calling must fail closed when call or contact permissions are unavailable");

        System.out.println("AndroidContactCallToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
