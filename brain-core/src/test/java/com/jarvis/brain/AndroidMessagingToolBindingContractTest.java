package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins structured send_message arguments through Android without lossy command-string reparsing. */
public final class AndroidMessagingToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        Path adapterPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidMessagingActions.java");
        Path factoryPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java");
        check(Files.exists(adapterPath), "Android must provide a typed messaging adapter");
        String adapter = Files.readString(adapterPath);
        String factory = Files.readString(factoryPath);

        check(adapter.contains("prepareMessage(String recipient, String message)"), "typed messaging adapter must accept recipient and message separately");
        check(adapter.contains("Intent.ACTION_SENDTO"), "messaging adapter must use Android SMS compose intent");
        check(adapter.contains("sms_body"), "messaging adapter must preserve message body as structured intent data");
        check(adapter.contains("ContactsContract.CommonDataKinds.Phone"), "messaging adapter must resolve named contacts when permitted");
        check(adapter.contains("Set<String> exactMatches"),
                "contact phone lookup must collect exact-name matches instead of selecting the first partial match");
        check(adapter.contains("return exactMatches.size() == 1 ? exactMatches.iterator().next() : null;"),
                "contact phone lookup must fail closed unless exactly one unique number is resolved");
        check(!adapter.contains("String fallback = null"),
                "contact phone lookup must not retain first-match fallback behavior");
        check(adapter.contains("couldn’t uniquely resolve a phone number"),
                "missing or ambiguous named-contact resolution must be reported truthfully");
        check(factory.contains("AndroidMessagingActions messaging = new AndroidMessagingActions(appContext)"), "tool factory must construct typed messaging adapter");
        check(factory.contains("args -> messaging.prepareMessage(args.get(\"recipient\"), args.get(\"message\"))"), "send_message must bind structured arguments directly");
        check(!factory.contains("actions.execute(\"text \" + args.get(\"recipient\") + \" \" + args.get(\"message\"))"), "send_message must not flatten structured data back into legacy text parsing");

        System.out.println("AndroidMessagingToolBindingContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
