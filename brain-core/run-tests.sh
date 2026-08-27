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
