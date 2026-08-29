package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Release builds must carry deterministic, non-UI provenance coverage for every shipped source/resource input. */
public final class ReleaseIntegrityContractTest {
    public static void main(String[] args) throws Exception {
        String gradle = Files.readString(Path.of("../android/app/build.gradle"));
        String workflow = Files.readString(Path.of("../.github/workflows/build-apk.yml"));

        check(gradle.contains("brain-core/src"),
                "release provenance must cover every shared-brain source/test input");
        check(gradle.contains("android/app/src"),
                "release provenance must cover every Android source, screen XML, drawable, and asset input");
        check(gradle.contains("eachFileRecurse"),
                "release provenance must recursively enumerate all files rather than a hand-picked subset");
        check(gradle.contains("MessageDigest.getInstance('SHA-256')") || gradle.contains("MessageDigest.getInstance(\"SHA-256\")"),
                "release provenance must cryptographically fingerprint covered files");
        check(gradle.contains("sort { a, b -> a.path <=> b.path }"),
                "release provenance manifest order must be deterministic");
        check(gradle.contains("generated/release-integrity/assets"),
                "release provenance must be embedded as a generated non-UI APK asset");
        check(gradle.contains("sourceSets.main.assets.srcDir"),
                "generated provenance asset must be wired into APK packaging");
        check(gradle.contains("tasks.named('preBuild')") || gradle.contains("tasks.named(\"preBuild\")"),
                "release provenance generation must be attached to Android preBuild");
        check(gradle.contains("dependsOn(generateReleaseIntegrity)"),
                "all Android builds must regenerate provenance before packaging");
        check(gradle.contains("SjRja040cGllcg=="),
                "release provenance must carry the encoded project-origin anchor");
        check(workflow.contains("assets/jarvis_provenance/origin.idx"),
                "APK verification must require the generated provenance asset to be physically packaged");
        check(workflow.contains("origin=SjRja040cGllcg=="),
                "APK verification must validate the encoded origin anchor after packaging");
        check(workflow.contains("grep -Eq '^root=[0-9a-f]{64}$'"),
                "APK verification must require a cryptographic manifest root after packaging");
        check(!gradle.contains("setText(\"J4ckN4pier\")") && !gradle.contains("android:text=\"J4ckN4pier\""),
                "ownership provenance must remain non-user-facing and must not alter frontend presentation");

        System.out.println("ReleaseIntegrityContractTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
