package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android production composition must attach a provider-neutral fresh-research boundary without baking in paid/non-commercial hosted APIs. */
public final class AndroidExternalResearchGatewayContractTest {
    public static void main(String[] args) throws Exception {
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));
        String adapter = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidExternalResearchGateway.java"));
        String workflow = Files.readString(Path.of("../.github/workflows/build-apk.yml"));
        Path debugReceiverPath = Path.of("../android/app/src/debug/java/com/jarvis/mobile/brain/AndroidExternalResearchGatewayTestReceiver.java");
        Path smokePath = Path.of("../.github/scripts/external-research-smoke.sh");

        check(runtime.contains("AndroidExternalResearchGateway.create(app"),
                "Android runtime must attach the production external-research adapter instead of ExternalResearchGateway.unavailable()");
        check(adapter.contains("implements ExternalResearchGateway"),
                "Android research adapter must stay behind the provider-neutral ExternalResearchGateway boundary");
        check(adapter.contains("new ResearchEvidence(") && adapter.contains(".toToolOutput()"),
                "fresh research results must retain source/freshness/confidence provenance");
        check(adapter.contains("research endpoint not configured"),
                "production research must fail closed when no endpoint is configured");
        check(adapter.contains("discoverPlaces(") && adapter.contains("resolveBusiness(")
                        && adapter.contains("weatherLookup("),
                "production adapter must cover the three mandatory fresh-research contracts");
        check(!adapter.contains("api.open-meteo.com") && !adapter.contains("nominatim.openstreetmap.org"),
                "commercial baseline must not hard-code public hosted APIs whose free-service terms are unsuitable as a product dependency");
        check(!adapter.contains("attemptReservation("),
                "fresh read-only research adapter must not silently enable consequential reservation submission");

        check(Files.exists(debugReceiverPath),
                "Android research transport must have a debug-only receiver for real emulator HTTP/provenance proof");
        check(Files.exists(smokePath),
                "Android research transport must have an Android-16 emulator smoke rather than source-only coverage");
        if (Files.exists(smokePath)) {
            String smoke = Files.readString(smokePath);
            check(smoke.contains("DEBUG_TEST_EXTERNAL_RESEARCH")
                            && smoke.contains("10.0.2.2")
                            && smoke.contains("JARVIS_RESEARCH_TEST_PASS")
                            && smoke.contains("CI_RESEARCH_MARKER_271828"),
                    "research smoke must prove emulator-to-host transport, structured provenance, and exact provider payload propagation");
        }
        check(workflow.contains("external-research-smoke.sh"),
                "full APK workflow must execute the Android research transport smoke");

        System.out.println("AndroidExternalResearchGatewayContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
