package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Core cross-cutting contracts must not quietly fall out of the composed brain exit gate. */
public final class BrainExitGateCompositionContractTest {
    public static void main(String[] args) throws Exception {
        String gate = Files.readString(Path.of("src/test/java/com/jarvis/brain/BrainExitGateAcceptanceTest.java"));
        require(gate, "TranslationGatewayContractTest.class", "provider-neutral translation truthfulness must be part of the composed brain gate");
        require(gate, "OrchestrationGatewayContractTest.class", "ranking/presentation/outcome truthfulness must be part of the composed brain gate");
        require(gate, "ConversationalCallOrchestratorTest.class", "provider-neutral conversational-call orchestration must be part of the composed brain gate");
        require(gate, "ConversationalCallToolContractTest.class", "conversational-call tool inputs, approval classification, and fail-closed fallback must be part of the composed brain gate");
        require(gate, "ConversationalCallToolTransportAdapterTest.class", "transport-backed conversational-call tool execution must be part of the composed brain gate");
        require(gate, "ExecutedPlanFollowupBridgeTest.class", "successful shared-plan execution must automatically feed the predictive follow-up system");
        require(gate, "AndroidExecutedPlanFollowupCompositionContractTest.class", "Android production must wire completed shared plans into its durable privacy-gated follow-up runtime");
        require(gate, "EmailCompositionToolContractTest.class", "provider-neutral email composition semantics must be part of the composed brain gate");
        require(gate, "AndroidEmailCompositionBindingContractTest.class", "Android email compose/review binding must be part of the composed brain gate");
        require(gate, "AndroidMediaToolBindingContractTest.class", "typed Android media search/play and transport controls must be part of the composed brain gate");
        require(gate, "AndroidAlarmToolBindingContractTest.class", "typed Android alarm creation must be part of the composed brain gate");
        require(gate, "AndroidVolumeToolBindingContractTest.class", "typed Android volume controls must be part of the composed brain gate");
        require(gate, "ResponseStyleContractTest.class", "central beta response style must be part of the composed brain gate");
        require(gate, "CommercialWakeWordPolicyTest.class", "commercial wake model provenance must be part of the composed brain gate");
        require(gate, "WakeWordArtifactVerifierTest.class", "wake model artifact integrity must be part of the composed brain gate");
        require(gate, "WakeWordReleaseTrustRegistryTest.class", "release-owned legal/provenance trust must be part of the composed brain gate");
        require(gate, "CompositeAssistantContextSourceTest.class", "provider-neutral reasoning context composition must be part of the composed brain gate");
        require(gate, "RuntimeEnvironmentContextSourceTest.class", "current time and relevant normalized device context must be part of the composed brain gate");
        require(gate, "KeywordGatedAssistantContextSourceTest.class", "potentially private reasoning context must remain unread unless the current request makes it relevant");
        require(gate, "WorkflowActionVersionContractTest.class", "release CI action-version integrity must be part of the composed brain gate");
        require(gate, "ToolRegistryOverrideSemanticsTest.class", "production tool overrides must retain their still-owned aliases without stealing aliases claimed elsewhere");
        require(gate, "MainActivityCleanRoomAudioContractTest.class", "production Activity donor-audio isolation must be part of the composed brain gate");
        require(gate, "AndroidMainActivityDecisionAffordanceContractTest.class", "full-app approval/recovery controls must be part of the composed brain gate");
        require(gate, "AndroidVoiceSessionDecisionAffordanceContractTest.class", "assistant-overlay approval/recovery controls and Android-16 decision proof must be part of the composed brain gate");
        require(gate, "UiListStorePersistenceTest.class", "durable editable UI lists must be part of the composed brain gate");
        require(gate, "JarvisUiListCompositionTest.class", "frontend/runtime UI list source-of-truth must be part of the composed brain gate");
        require(gate, "RoutineStorePersistenceTest.class", "durable user-created routines must be part of the composed brain gate");
        require(gate, "JarvisUiRoutineCompositionTest.class", "frontend/runtime routine source-of-truth must be part of the composed brain gate");
        require(gate, "ActivityLogPersistenceTest.class", "durable user-visible activity audit state must be part of the composed brain gate");
        require(gate, "JarvisUiActivityCompositionTest.class", "frontend/runtime activity source-of-truth must be part of the composed brain gate");
        require(gate, "DeviceStateStorePersistenceTest.class", "durable normalized device state must be part of the composed brain gate");
        require(gate, "JarvisUiDeviceCompositionTest.class", "frontend/runtime device source-of-truth must be part of the composed brain gate");
        require(gate, "MusicQueueStorePersistenceTest.class", "durable music queue/playback state must be part of the composed brain gate");
        require(gate, "JarvisUiMusicCompositionTest.class", "frontend/runtime music source-of-truth must be part of the composed brain gate");
        require(gate, "PendingApprovalSideQuestionTest.class", "pending consequential approvals must survive safe side questions in the composed brain gate");
        require(gate, "PendingRecoverySideQuestionTest.class", "pending recovery decisions must survive safe side questions in the composed brain gate");
        require(gate, "PendingConsequentialInterruptionTest.class", "a second consequential request must remain unqueued while an approval or recovery decision is pending");
        require(gate, "PendingDecisionInterruptionPolicyTest.class", "pending runtime decisions must use explicit ASK/DO_BOTH interruption policy in the composed brain gate");
        require(gate, "PendingDecisionSurfaceContinuityTest.class", "pending decision affordances must survive side answers in the composed brain gate");
        require(gate, "LegacyAndroidActionRouterNamedTargetSafetyContractTest.class", "legacy raw-command contact guessing must remain isolated from production typed tools in the composed brain gate");
        System.out.println("BrainExitGateCompositionContractTest passed");
    }
    private static void require(String source, String token, String message) { if (!source.contains(token)) throw new AssertionError(message + ": missing " + token); }
}
