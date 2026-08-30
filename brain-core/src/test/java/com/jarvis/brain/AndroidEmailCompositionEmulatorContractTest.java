package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins an API-36 debug-only round trip for the real Android email compose adapter. */
public final class AndroidEmailCompositionEmulatorContractTest {
    public static void main(String[] args) throws Exception {
        Path manifestPath = Path.of("../android/app/src/debug/AndroidManifest.xml");
        Path receiverPath = Path.of("../android/app/src/debug/java/com/jarvis/mobile/actions/JarvisEmailTestReceiver.java");
        Path capturePath = Path.of("../android/app/src/debug/java/com/jarvis/mobile/actions/JarvisEmailCaptureActivity.java");
        Path smokePath = Path.of("../.github/scripts/email-compose-smoke.sh");
        Path workflowPath = Path.of("../.github/workflows/build-apk.yml");

        check(Files.exists(receiverPath), "debug build must expose an email-action test receiver");
        check(Files.exists(capturePath), "debug build must expose an email-capable capture activity");
        check(Files.exists(smokePath), "Android-16 lane must include a dedicated email compose smoke script");

        String manifest = Files.readString(manifestPath);
        String receiver = Files.readString(receiverPath);
        String capture = Files.readString(capturePath);
        String smoke = Files.readString(smokePath);
        String workflow = Files.readString(workflowPath);

        check(manifest.contains("JarvisEmailTestReceiver"), "debug manifest must register the email test receiver");
        check(manifest.contains("com.jarvis.mobile.DEBUG_TEST_EMAIL"), "debug receiver must use an explicit CI-only action");
        check(manifest.contains("JarvisEmailCaptureActivity"), "debug manifest must register an email capture activity");
        check(manifest.contains("android.intent.action.SENDTO"), "debug capture activity must resolve ACTION_SENDTO");
        check(manifest.contains("android:scheme=\"mailto\""), "debug capture activity must be restricted to mailto URIs");

        check(receiver.contains("new AndroidEmailActions(context)"), "device proof must invoke the production Android email adapter");
        check(receiver.contains("person+tag@example.com"), "device proof must exercise a recipient requiring URI encoding");
        check(receiver.contains("Subject & details"), "device proof must preserve a structured subject");
        check(receiver.contains("Body line one"), "device proof must preserve a structured body");
        check(receiver.contains("JARVIS_EMAIL_ACTION_RESULT"), "device proof receiver must expose the adapter result to CI");

        check(capture.contains("getEncodedSchemeSpecificPart"), "capture activity must expose encoded mailto evidence");
        check(capture.contains("getSchemeSpecificPart"), "capture activity must expose decoded recipient evidence");
        check(capture.contains("Intent.EXTRA_SUBJECT"), "capture activity must inspect the subject extra");
        check(capture.contains("Intent.EXTRA_TEXT"), "capture activity must inspect the body extra");
        check(capture.contains("JARVIS_EMAIL_CAPTURE"), "capture activity must emit structured CI evidence");

        check(smoke.contains("com.jarvis.mobile.DEBUG_TEST_EMAIL"), "Android-16 smoke must trigger the real email adapter");
        check(smoke.contains("JARVIS_EMAIL_ACTION_RESULT.*Email draft ready for person+tag@example.com"),
                "Android-16 smoke must prove the adapter reports a draft/review handoff, not sending");
        check(smoke.contains("JARVIS_EMAIL_CAPTURE.*encoded=person%2Btag%40example.com"),
                "Android-16 smoke must prove mailto recipient encoding");
        check(smoke.contains("JARVIS_EMAIL_CAPTURE.*decoded=person+tag@example.com"),
                "Android-16 smoke must prove the intended recipient survives decoding");
        check(smoke.contains("JARVIS_EMAIL_CAPTURE.*subject=Subject & details"),
                "Android-16 smoke must prove structured subject handoff");
        check(smoke.contains("JARVIS_EMAIL_CAPTURE.*body=Body line one"),
                "Android-16 smoke must prove structured body handoff");
        check(workflow.contains("sh .github/scripts/email-compose-smoke.sh"),
                "APK workflow must execute the dedicated email compose smoke inside Android 16");

        System.out.println("AndroidEmailCompositionEmulatorContractTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
