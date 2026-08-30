package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** open_dialer must invoke Android's dialer capability directly rather than guessing an app label. */
public final class AndroidDialerToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        Path dialerPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidDialerActions.java");
        check(Files.exists(dialerPath), "Android production must provide a typed dialer action adapter");
        String dialer = Files.readString(dialerPath);

        check(factory.contains("AndroidDialerActions dialer = new AndroidDialerActions(appContext)"),
                "Android tool registry must compose the typed dialer adapter");
        check(factory.contains("args -> dialer.openDialer()"),
                "open_dialer must bind directly to the typed Android dialer action");
        check(!factory.contains("args -> actions.execute(\"open phone\")"),
                "open_dialer must not depend on an installed app being labeled phone");
        check(dialer.contains("Intent.ACTION_DIAL"),
                "typed dialer action must use Android's ACTION_DIAL capability");
        check(dialer.contains("resolveActivity(context.getPackageManager()) == null"),
                "typed dialer action must fail closed when no dialer can resolve the intent");
        check(dialer.contains("Dialer opened."),
                "typed dialer action must report the actual UI outcome rather than a sent/call claim");

        System.out.println("AndroidDialerToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
