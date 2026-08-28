#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
javac -d out $(find src/main/java src/test/java -name '*.java' | sort)
java -cp out com.jarvis.brain.BrainAcceptanceTest
java -cp out com.jarvis.brain.BrainAdvancedAcceptanceTest
java -cp out com.jarvis.brain.BrainProviderAcceptanceTest
java -cp out com.jarvis.brain.AssistantBenchmarkTest
java -cp out com.jarvis.brain.PlanValidationTest
java -cp out com.jarvis.brain.AttentionConversationTest
java -cp out com.jarvis.brain.HabitPredictionTest
java -cp out com.jarvis.brain.StructuredPlanningTest
java -cp out com.jarvis.brain.LongTermMemoryTest
java -cp out com.jarvis.brain.MemoryConsolidationTest
java -cp out com.jarvis.brain.ResumableExecutionTest
java -cp out com.jarvis.brain.PolicyProviderRouterTest
java -cp out com.jarvis.brain.PlanRepairCoordinatorTest
java -cp out com.jarvis.brain.ClaudeHardeningTest
java -cp out com.jarvis.brain.MemoryLifecycleTest
java -cp out com.jarvis.brain.AssistantProviderIntegrationTest
java -cp out com.jarvis.brain.AssistantContextIntegrationTest
java -cp out com.jarvis.brain.ConversationContinuityTest
java -cp out com.jarvis.brain.PendingClarificationTest
java -cp out com.jarvis.brain.PlanStateAndRetentionTest
java -cp out com.jarvis.brain.ProductionSafetyContractTest
java -cp out com.jarvis.brain.PassiveWakeConversationTest
java -cp out com.jarvis.brain.ProactiveExecutiveTest
java -cp out com.jarvis.brain.SessionWorkingMemoryTest
java -cp out com.jarvis.brain.SemanticReflexIntegrationTest
java -cp out com.jarvis.brain.SessionStateDeltaTest
java -cp out com.jarvis.brain.ProactiveSafetyPolicyTest
