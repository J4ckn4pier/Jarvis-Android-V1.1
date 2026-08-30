package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins real Android email composition with contact-email resolution and a user-visible review surface. */
public final class AndroidEmailCompositionBindingContractTest {
    public static void main(String[] args) throws Exception {
        Path adapterPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidEmailActions.java");
        Path factoryPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java");
        check(Files.exists(adapterPath), "Android must provide a typed email composition adapter");

        String adapter = Files.readString(adapterPath);
        String factory = Files.readString(factoryPath);
        check(adapter.contains("prepareEmail(String recipient, String subject, String body)"),
                "typed email adapter must preserve recipient/subject/body separately");
        check(adapter.contains("Intent.ACTION_SENDTO"), "email adapter must open a compose/review surface rather than send silently");
        check(adapter.contains("mailto:"), "email compose intent must be constrained to email-capable apps");
        check(adapter.contains("Intent.EXTRA_SUBJECT"), "email adapter must preserve structured subject");
        check(adapter.contains("Intent.EXTRA_TEXT"), "email adapter must preserve structured body");
        check(adapter.contains("ContactsContract.CommonDataKinds.Email"),
                "email adapter must resolve a named contact's email when Contacts permission is available");
        check(factory.contains("AndroidEmailActions email = new AndroidEmailActions(appContext)"),
                "Android tool factory must construct the typed email adapter");
        check(factory.contains("args -> email.prepareEmail(args.get(\"recipient\"), args.get(\"subject\"), args.get(\"body\"))"),
                "compose_email must bind structured arguments directly without command-string reparsing");

        System.out.println("AndroidEmailCompositionBindingContractTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
