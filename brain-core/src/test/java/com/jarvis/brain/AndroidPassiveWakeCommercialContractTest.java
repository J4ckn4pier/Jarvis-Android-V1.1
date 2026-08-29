package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Passive wake must be local, lifecycle-owned, and disabled unless model provenance is commercially approved. */
public final class AndroidPassiveWakeCommercialContractTest {
    public static void main(String[] args) throws Exception {
        String port = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/WakeWordDetectorPort.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/AndroidWakeWordDetectorFactory.java"));
        String service = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceInteractionService.java"));

        check(port.contains("WakeWordModelDescriptor modelDescriptor()"),
                "detector port must expose model provenance to the commercial gate");
        check(port.contains("boolean start(Runnable onWake)"),
                "detector port must expose local wake callback startup");
        check(port.contains("void stop()"), "detector port must be lifecycle stoppable");

        check(factory.contains("CommercialWakeWordPolicy"),
                "Android factory must evaluate wake model commercial provenance");
        check(factory.contains("return new DisabledWakeWordDetector"),
                "unapproved or absent model must fail closed instead of enabling passive microphone capture");
        check(factory.contains("commercial wake model not configured"),
                "beta without a licensed model must report a truthful disabled reason");

        check(service.contains("WakeWordDetectorPort wakeWordDetector"),
                "voice interaction service must own one passive wake detector");
        check(service.contains("wakeWordDetector = AndroidWakeWordDetectorFactory.create(this)"),
                "voice service must create detector through the commercial-safe factory");
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
