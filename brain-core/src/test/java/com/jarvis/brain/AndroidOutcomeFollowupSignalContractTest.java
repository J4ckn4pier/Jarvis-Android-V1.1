package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins the Android platform boundary to semantic follow-up events rather than raw telemetry-shaped args. */
public final class AndroidOutcomeFollowupSignalContractTest {
    public static void main(String[] args) throws Exception {
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        String runtime = Files.readString(runtimePath);

        check(runtime.contains("onOutcomeFollowupSignal(OutcomeFollowupSignal signal)"),
                "Android runtime must accept the shared semantic OutcomeFollowupSignal type");
        check(runtime.contains("followups.onSignal(signal)"),
                "Android runtime must forward semantic signals directly to the privacy-owned brain runtime");
        check(!runtime.contains("onOutcomeFollowupSignal(String episodeId"),
                "Android platform boundary must not expose decomposed follow-up fields");
        check(!runtime.toLowerCase().contains("latitude") && !runtime.toLowerCase().contains("longitude")
                        && !runtime.toLowerCase().contains("geofence"),
                "Android brain composition must not accept raw location/geofence telemetry");

        System.out.println("AndroidOutcomeFollowupSignalContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
