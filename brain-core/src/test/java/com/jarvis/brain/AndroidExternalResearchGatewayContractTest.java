package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android production composition must attach provider-neutral fresh research and preserve a narrow local-network trust boundary. */
public final class AndroidExternalResearchGatewayContractTest {
    public static void main(String[] args) throws Exception {
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));
        String adapter = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidExternalResearchGateway.java"));
        String httpJson = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers/HttpJson.java"));
        String networkSecurity = Files.readString(Path.of("../android/app/src/main/res/xml/network_security_config.xml"));
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
        check(adapter.contains("setInstanceFollowRedirects(false)"),
                "research transport must not silently follow redirects away from the user-configured endpoint trust boundary");

        check(EndpointTransportPolicy.allows("https://provider.example/v1"),
                "normal external providers must remain HTTPS-capable");
        check(EndpointTransportPolicy.allows("http://127.0.0.1:11434/v1"),
                "loopback local AI must remain available without paid hosting");
        check(EndpointTransportPolicy.allows("http://10.0.2.2:11434/v1"),
                "Android emulator host bridge must remain available for real device proof");
        check(EndpointTransportPolicy.allows("http://jarvis.local:11434/v1"),
                "user-owned mDNS local AI endpoints must be usable without requiring public TLS hosting");
        check(!EndpointTransportPolicy.allows("http://provider.example/v1"),
                "ordinary internet HTTP must remain fail-closed");
        check(!EndpointTransportPolicy.allows("http://jarvis.local.evil.example/v1"),
                "mDNS allowance must not be bypassable with a lookalike internet hostname");
        check(adapter.contains("EndpointTransportPolicy.allows(endpoint)"),
                "external research transport must use the shared endpoint trust policy");
        check(httpJson.contains("EndpointTransportPolicy.allows(endpoint)"),
                "all cortex provider HTTP transport must use the same endpoint trust policy");
        check(networkSecurity.contains("<domain includeSubdomains=\"true\">local</domain>"),
                "Android network security must permit only the mDNS local namespace in addition to existing loopback cleartext hosts");
        check(networkSecurity.contains("<base-config cleartextTrafficPermitted=\"false\""),
                "general Android cleartext internet traffic must remain disabled");

        check(Files.exists(debugReceiverPath),
                "Android research transport must have a debug-only receiver for real emulator HTTP/provenance proof");
        if (Files.exists(debugReceiverPath)) {
            String receiver = Files.readString(debugReceiverPath);
            check(receiver.contains("goAsync()") && receiver.contains("new Thread(") && receiver.contains("pending.finish()"),
                    "debug research transport probe must run network I/O off Android's main thread and finish its async broadcast");
        }
        check(Files.exists(smokePath),
                "Android research transport must preserve an Android-16 emulator smoke rather than source-only coverage");
        if (Files.exists(smokePath)) {
            String smoke = Files.readString(smokePath);
            check(smoke.contains("DEBUG_TEST_EXTERNAL_RESEARCH")
                            && smoke.contains("10.0.2.2")
                            && smoke.contains("JARVIS_RESEARCH_TEST_PASS")
                            && smoke.contains("CI_RESEARCH_MARKER_271828"),
                    "research smoke must prove emulator-to-host transport, structured provenance, and exact provider payload propagation");
        }

        System.out.println("AndroidExternalResearchGatewayContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
