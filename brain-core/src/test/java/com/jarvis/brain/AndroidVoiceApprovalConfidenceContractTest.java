package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Spoken approval must remain fail-closed when ASR confidence is too low. */
public final class AndroidVoiceApprovalConfidenceContractTest {
    public static void main(String[] args) throws Exception {
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));
        String conversation = Files.readString(Path.of("src/main/java/com/jarvis/brain/RuntimeApprovalConversation.java"));

        check(runtime.contains("conversation.handle(utterance, speechConfidence)"),
                "Android speech confidence must reach the approval conversation boundary");
        check(conversation.contains("MIN_VOICE_APPROVAL_CONFIDENCE"),
                "approval conversation must define a minimum spoken approval confidence");
        check(conversation.contains("handle(String utterance, double confidence)"),
                "approval conversation must accept confidence for spoken turns");
        check(conversation.contains("safeConfidence(confidence)"),
                "recognizer confidence must be sanitized before approval decisions");
        check(conversation.contains("unclearApproval()") && conversation.contains("unclearRecovery()"),
                "low-confidence approval/retry language must preserve the pending state instead of executing");
        check(conversation.contains("AssistantSurfaceState.AWAITING_APPROVAL") && conversation.contains("AssistantSurfaceState.NEEDS_INPUT"),
                "low-confidence replies must preserve the pending approval/recovery state instead of cancelling it");
        check(conversation.contains("handle(utterance,1.0)") || conversation.contains("handle(utterance, 1.0)"),
                "trusted typed/direct surfaces must keep an explicit full-confidence path");

        System.out.println("AndroidVoiceApprovalConfidenceContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
