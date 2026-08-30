package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Finished-product safety/clean-room contracts must execute in the shared acceptance runner, not merely exist in source. */
public final class CriticalCleanRoomRunnerCoverageTest {
    public static void main(String[] args) throws Exception {
        String runner = Files.readString(Path.of("run-tests.sh"));

        require(runner, "MainActivityCleanRoomAudioContractTest",
                "production Activity donor-audio isolation contract must execute in the shared runner");
        require(runner, "VoiceSessionCleanRoomContractTest",
                "voice-session donor-audio isolation contract must remain in the shared runner");
        require(runner, "DonorAssetRemovalContractTest",
                "donor asset removal contract must remain in the shared runner");
        require(runner, "CleanRoomApplicationIdentityContractTest",
                "clean-room application identity contract must remain in the shared runner");
        require(runner, "AndroidAccessibilityToolBindingContractTest",
                "production accessibility tool binding contract must execute in the shared runner");
        require(runner, "AndroidUiInteractionToolBindingContractTest",
                "approval-gated UI interaction contract must execute in the shared runner");

        System.out.println("CriticalCleanRoomRunnerCoverageTest passed");
    }

    private static void require(String source, String token, String message) {
        if (!source.contains(token)) throw new AssertionError(message + ": missing " + token);
    }
}
