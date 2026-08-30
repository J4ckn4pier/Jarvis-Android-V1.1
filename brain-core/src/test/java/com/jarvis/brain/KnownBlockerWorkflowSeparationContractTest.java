package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Device-smoke CI may bypass known product blockers only when a separate brain workflow still enforces them. */
public final class KnownBlockerWorkflowSeparationContractTest {
    public static void main(String[] args) throws Exception {
        String runner = Files.readString(Path.of("run-tests.sh"));
        String build = Files.readString(Path.of("../.github/workflows/build-apk.yml"));
        String brain = Files.readString(Path.of("../.github/workflows/brain-core.yml"));

        check(runner.contains("JARVIS_SKIP_KNOWN_BLOCKERS"),
                "runner must expose an explicit, narrowly named known-blocker bypass for device regression CI");
        check(runner.contains("AndroidConversationalCallBindingContractTest"),
                "full runner must continue to enforce the real conversational-call blocker");
        check(build.contains("JARVIS_SKIP_KNOWN_BLOCKERS: 'true'"),
                "APK workflow must opt in explicitly when it needs to continue into Android compile/emulator smoke");
        check(build.contains("bash brain-core/run-tests.sh"),
                "APK workflow must still execute the shared regression suite");
        check(build.contains("pull_request:"),
                "APK workflow must verify pull-request branch updates, not only direct pushes");
        check(build.contains("branches: [main]"),
                "APK pull-request verification must target integration into main");
        check(build.contains("name: Preserve Android failure evidence"),
                "APK workflow must preserve emulator evidence when Android verification fails");
        check(build.contains("if: failure()"),
                "failure-evidence upload must execute even after an earlier Android verification failure");
        check(build.contains("android/app/build/outputs/apk/debug/"),
                "failure-evidence upload must retain generated Android logs, UI trees, screenshots, and launch traces");
        check(build.contains("if-no-files-found: warn"),
                "failure-evidence upload must not hide the original failure when only partial evidence exists");
        check(!brain.contains("JARVIS_SKIP_KNOWN_BLOCKERS"),
                "authoritative Brain Core workflow must never bypass known finished-product blockers");
        check(brain.contains("./run-tests.sh"),
                "authoritative Brain Core workflow must execute the full runner");

        System.out.println("KnownBlockerWorkflowSeparationContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
