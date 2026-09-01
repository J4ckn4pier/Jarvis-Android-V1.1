package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Hands-free speech interruption is allowed only behind an Android AEC-gated monitor. */
public final class AndroidVoiceAecBargeInContractTest {
    public static void main(String[] args) throws Exception {
        String monitor = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/AndroidAecBargeInMonitor.java"));
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));

        check(monitor.contains("AcousticEchoCanceler.isAvailable()"),
                "hands-free barge-in must fail closed when Android AEC is unavailable");
        check(monitor.contains("private boolean isAecAvailableSafely()"),
                "Samsung/OEM exceptions while probing AEC availability must be contained at the barge-in boundary");
        check(monitor.contains("private int minimumBufferSizeSafely()"),
                "Samsung/OEM exceptions while probing AudioRecord minimum buffer size must be contained at the barge-in boundary");
        check(monitor.contains("private boolean isInitializedSafely(AudioRecord recorder)"),
                "Samsung/OEM exceptions while reading AudioRecord initialization state must be contained before microphone ownership changes");
        check(monitor.contains("context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)"),
                "barge-in capture must explicitly re-check runtime microphone permission at the capture boundary");
        check(monitor.contains("PackageManager.PERMISSION_GRANTED"),
                "barge-in capture may only proceed with granted microphone permission");
        check(monitor.contains("catch (SecurityException"),
                "barge-in capture must fail closed if microphone permission is revoked between check and AudioRecord creation");
        check(monitor.contains("MediaRecorder.AudioSource.VOICE_COMMUNICATION"),
                "barge-in microphone path must request the communication audio source for echo handling");
        check(monitor.contains("AcousticEchoCanceler.create"),
                "barge-in monitor must attach AEC to its AudioRecord session");
        check(monitor.contains("REQUIRED_HOT_FRAMES"),
                "barge-in must require sustained speech energy rather than a single noisy frame");
        check(session.contains("AndroidAecBargeInMonitor bargeInMonitor"),
                "voice session must own the AEC barge-in monitor");
        check(session.contains("bargeInMonitor.start(this::handleHandsFreeBargeIn)"),
                "JARVIS speech must arm hands-free interruption through the AEC monitor");
        check(session.contains("private void handleHandsFreeBargeIn()"),
                "voice session must have one explicit hands-free interruption boundary");
        check(session.contains("bargeInMonitor.stop();"),
                "normal TTS completion and lifecycle shutdown must disarm the barge-in monitor");

        System.out.println("AndroidVoiceAecBargeInContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
