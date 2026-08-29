package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Core cross-cutting contracts must not quietly fall out of the composed brain exit gate. */
public final class BrainExitGateCompositionContractTest {
    public static void main(String[] args) throws Exception {
        String gate = Files.readString(Path.of("src/test/java/com/jarvis/brain/BrainExitGateAcceptanceTest.java"));

        require(gate, "TranslationGatewayContractTest.class",
                "provider-neutral translation truthfulness must be part of the composed brain gate");
        require(gate, "OrchestrationGatewayContractTest.class",
                "ranking/presentation/outcome truthfulness must be part of the composed brain gate");
        require(gate, "ResponseStyleContractTest.class",
                "central beta response style must be part of the composed brain gate");
        require(gate, "UiListStorePersistenceTest.class",
                "durable editable UI lists must be part of the composed brain gate");
        require(gate, "JarvisUiListCompositionTest.class",
                "frontend/runtime UI list source-of-truth must be part of the composed brain gate");
        require(gate, "RoutineStorePersistenceTest.class",
                "durable user-created routines must be part of the composed brain gate");
        require(gate, "JarvisUiRoutineCompositionTest.class",
                "frontend/runtime routine source-of-truth must be part of the composed brain gate");
        require(gate, "ActivityLogPersistenceTest.class",
                "durable user-visible activity audit state must be part of the composed brain gate");
        require(gate, "JarvisUiActivityCompositionTest.class",
                "frontend/runtime activity source-of-truth must be part of the composed brain gate");
        require(gate, "DeviceStateStorePersistenceTest.class",
                "durable normalized device state must be part of the composed brain gate");
        require(gate, "JarvisUiDeviceCompositionTest.class",
                "frontend/runtime device source-of-truth must be part of the composed brain gate");

        System.out.println("BrainExitGateCompositionContractTest passed");
    }

    private static void require(String source, String token, String message) {
        if (!source.contains(token)) throw new AssertionError(message + ": missing " + token);
    }
}
