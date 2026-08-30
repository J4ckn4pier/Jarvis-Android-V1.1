package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** CI must not rely on deprecated Node-20-targeted Java/checkout actions for release acceptance. */
public final class WorkflowActionVersionContractTest {
    public static void main(String[] args) throws Exception {
        String brain = Files.readString(Path.of("../.github/workflows/brain-core.yml"));
        String apk = Files.readString(Path.of("../.github/workflows/build-apk.yml"));

        require(brain, "actions/checkout@v5", "Brain workflow must use current checkout action");
        require(brain, "actions/setup-java@v5", "Brain workflow must use current Java setup action");
        require(apk, "actions/checkout@v5", "APK workflow must use current checkout action");
        require(apk, "actions/setup-java@v5", "APK workflow must use current Java setup action");
        reject(brain, "actions/checkout@v4", "Brain workflow must not retain deprecated checkout v4");
        reject(brain, "actions/setup-java@v4", "Brain workflow must not retain deprecated setup-java v4");
        reject(apk, "actions/checkout@v4", "APK workflow must not retain deprecated checkout v4");
        reject(apk, "actions/setup-java@v4", "APK workflow must not retain deprecated setup-java v4");

        System.out.println("WorkflowActionVersionContractTest passed");
    }

    private static void require(String source, String token, String message) {
        if (!source.contains(token)) throw new AssertionError(message + ": missing " + token);
    }

    private static void reject(String source, String token, String message) {
        if (source.contains(token)) throw new AssertionError(message + ": found " + token);
    }
}
