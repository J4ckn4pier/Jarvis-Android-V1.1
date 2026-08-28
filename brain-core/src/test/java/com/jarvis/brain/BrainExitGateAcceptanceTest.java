package com.jarvis.brain;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Cross-cutting brain exit gate.
 *
 * This is deliberately separate from feature-local tests: it composes the major
 * acceptance contracts that must remain green together before Android smoke
 * testing is allowed to resume. It does not claim device readiness.
 */
public final class BrainExitGateAcceptanceTest {
    private static final List<Class<?>> GATES = List.of(
            BrainAcceptanceTest.class,
            BrainAdvancedAcceptanceTest.class,
            BrainProviderAcceptanceTest.class,
            AssistantBenchmarkTest.class,
            AssistantExecutiveIntegrationTest.class,
            ConversationContinuityTest.class,
            PendingClarificationTest.class,
            PlanStateAndRetentionTest.class,
            ResumableExecutionTest.class,
            ExecutionRecoveryContractTest.class,
            ExecutiveObservationLoopTest.class,
            ExternalResearchGatewayContractTest.class,
            SessionTrustBoundaryTest.class,
            SessionStateDeltaTest.class,
            ProviderAttemptBudgetTest.class,
            ProviderRecoveryTest.class,
            ProductionSafetyContractTest.class,
            ProactiveSafetyPolicyTest.class,
            WakeBargeInCompositionTest.class,
            ToolCapableLocalProviderTest.class,
            ToolContractSelectorTest.class,
            LongTermMemoryTest.class,
            MemoryConsolidationTest.class,
            MemoryLifecycleTest.class
    );

    private BrainExitGateAcceptanceTest() {}

    public static void main(String[] args) throws Exception {
        int passed = 0;
        for (Class<?> gate : GATES) {
            runGate(gate);
            passed++;
        }
        if (passed != GATES.size()) {
            throw new AssertionError("Brain exit gate did not execute every required acceptance contract");
        }
        System.out.println("BrainExitGateAcceptanceTest: PASS (" + passed + " composed gates)");
        System.out.println("NOTE: composed JVM brain verified; Android/device smoke still required separately.");
    }

    private static void runGate(Class<?> gate) throws Exception {
        Method main = gate.getMethod("main", String[].class);
        try {
            main.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException("Acceptance gate failed: " + gate.getSimpleName(), cause);
        }
    }
}
