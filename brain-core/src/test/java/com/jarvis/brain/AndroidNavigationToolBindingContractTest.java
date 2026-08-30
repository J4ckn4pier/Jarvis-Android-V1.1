package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** navigate(destination) must preserve structured destination data into Android's navigation intent. */
public final class AndroidNavigationToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        Path navPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidNavigationActions.java");
        check(Files.exists(navPath), "Android production must provide a typed navigation action adapter");
        String nav = Files.readString(navPath);

        check(factory.contains("AndroidNavigationActions navigation = new AndroidNavigationActions(appContext)"),
                "Android tool registry must compose the typed navigation adapter");
        check(factory.contains("args -> navigation.navigate(args.get(\"destination\"))"),
                "navigate must preserve the structured destination argument");
        check(!factory.contains("actions.execute(\"navigate to \" + args.get(\"destination\"))"),
                "navigate must not flatten structured destination into the legacy parser");
        check(nav.contains("Intent.ACTION_VIEW"), "typed navigation action must use ACTION_VIEW");
        check(nav.contains("Uri.encode(clean)"), "destination must be URI encoded as structured navigation data");
        check(nav.contains("geo:0,0?q="), "navigation intent must use the generic geo query contract");
        check(nav.contains("resolveActivity(context.getPackageManager()) == null"),
                "navigation must fail closed when no compatible app resolves the intent");

        System.out.println("AndroidNavigationToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
