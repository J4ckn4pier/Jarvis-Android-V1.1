package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Ensures every executable brain-core test is actually wired into the shared acceptance runner. */
public final class SharedRunnerCompletenessContractTest {
    public static void main(String[] args) throws Exception {
        String runner = Files.readString(Path.of("run-tests.sh"));
        Path testDir = Path.of("src/test/java/com/jarvis/brain");

        List<String> missing;
        try (var paths = Files.list(testDir)) {
            missing = paths
                    .filter(path -> path.getFileName().toString().endsWith("Test.java"))
                    .map(path -> path.getFileName().toString().replace(".java", ""))
                    .filter(name -> !runner.contains(name))
                    .sorted()
                    .toList();
        }

        if (!missing.isEmpty()) {
            throw new AssertionError("shared acceptance runner omits test classes: " + missing);
        }

        System.out.println("SharedRunnerCompletenessContractTest passed");
    }
}
