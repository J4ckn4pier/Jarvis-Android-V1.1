package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Passive wake must stay release-owned: redistributed models are fingerprint-gated; platform speech is explicitly allowlisted. */
public final class AndroidPassiveWakeCommercialContractTest {
    public static void main(String[] args) throws Exception {
        String port = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/WakeWordDetectorPort.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/AndroidWakeWordDetectorFactory.java"));
        String detector = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/AndroidOnDeviceWakeWordDetector.java"));
        String registry = Files.readString(Path.of("src/main/java/com/jarvis/brain/WakeWordReleaseTrustRegistry.java"));
        String service = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceInteractionService.java"));

        check(port.contains("WakeWordModelDescriptor modelDescriptor()"),
                "detector port must expose provenance to the release trust boundary");
        check(port.contains("boolean start(Runnable onWake)"), "detector port must expose local wake callback startup");
        check(port.contains("void stop()"), "detector port must be lifecycle stoppable");

        check(registry.contains("CommercialWakeWordPolicy currentPolicy()"),
                "release registry must retain fingerprint policy for any redistributed wake model artifact");
        check(registry.contains("isPlatformManagedServiceApproved"),
                "release registry must explicitly approve platform-managed wake services rather than letting runtime metadata self-authorize");
        check(factory.contains("WakeWordReleaseTrustRegistry.isPlatformManagedServiceApproved"),
                "Android factory must evaluate platform speech against the release-owned allowlist");
        check(factory.contains("return new DisabledWakeWordDetector"),
                "unapproved platform service must fail closed");

        check(detector.contains("platform-managed-not-redistributed"),
                "Android speech descriptor must state that no wake model artifact is redistributed by JARVIS");
        check(detector.contains("\"\",\n                \"platform-managed-not-redistributed\""),
                "platform speech must not fabricate an artifact SHA");
        check(detector.contains("false,\n                true"),
                "platform speech descriptor must not claim JARVIS has redistribution rights to an artifact it does not ship");

        check(service.contains("WakeWordDetectorPort wakeWordDetector"),
                "voice interaction service must own one passive wake detector");
        check(service.contains("return AndroidWakeWordDetectorFactory.create(this)"),
                "voice service must create detector through the release-safe factory, including when wrapped by an OEM exception boundary");
        check(service.contains("wakeWordDetector.start(this::showWakeSession)"),
                "voice service must start passive wake only through the detector port");
        check(service.contains("private void showWakeSession()"),
                "wake callback must have one explicit system-session activation boundary");
        check(service.contains("wakeWordDetector.stop()"),
                "voice service shutdown must stop passive wake capture");

        System.out.println("AndroidPassiveWakeCommercialContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
