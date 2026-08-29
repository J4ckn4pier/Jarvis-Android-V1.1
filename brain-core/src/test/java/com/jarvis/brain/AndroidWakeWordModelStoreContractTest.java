package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android wake models must be app-private, metadata-backed, hash verified, and min-SDK compatible. */
public final class AndroidWakeWordModelStoreContractTest {
    public static void main(String[] args) throws Exception {
        String store = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/AndroidWakeWordModelStore.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/AndroidWakeWordDetectorFactory.java"));

        check(store.contains("getNoBackupFilesDir()"), "wake model artifact must live in app-private no-backup storage");
        check(store.contains("wake-model.properties"), "wake model must have explicit provenance metadata");
        check(store.contains("wake-model.bin"), "wake model bytes must use a distinct app-private artifact file");
        check(store.contains("MessageDigest.getInstance(\"SHA-256\")"), "wake model bytes must be SHA-256 verified");
        check(store.contains("WakeWordArtifactVerifier"), "Android loader must reuse the core commercial+integrity gate");
        check(store.contains("WakeWordReleaseTrustRegistry.currentPolicy()"),
                "Android loader must use the release-owned legal/provenance trust registry rather than mutable metadata alone");
        check(store.contains("commercialRedistributionAllowed"), "metadata must record commercial redistribution permission");
        check(store.contains("trainingDataProvenanceVerified"), "metadata must record training-data provenance status");
        check(!store.contains("java.util.HexFormat"), "minSdk 29 path must not depend on newer HexFormat runtime API");
        check(store.contains("private static String toHex(byte[] bytes)"), "wake loader must provide a min-SDK-safe hex encoder");
        check(factory.contains("new AndroidWakeWordModelStore(context).loadApproved()"),
                "detector factory must consult approved app-private model store before creating a detector");

        System.out.println("AndroidWakeWordModelStoreContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
