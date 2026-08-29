package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android assistant overlay must expose the same decision semantics and device-proof as the full app. */
public final class AndroidVoiceSessionDecisionAffordanceContractTest {
    public static void main(String[] args) throws Exception {
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));

        check(session.contains("primaryButton.setContentDescription(recovery ? \"JARVIS RETRY action\" : \"JARVIS APPROVE action\");"),
                "assistant overlay primary decision control must reuse the full-app accessibility identifier space");
        check(session.contains("cancelButton.setContentDescription(\"JARVIS CANCEL action\");"),
                "assistant overlay cancel control must reuse the full-app accessibility identifier space");

        check(smoke.contains("JARVIS_OVERLAY_SESSION_SHOWN"),
                "emulator smoke must distinguish a shown assistant session before decision-node lookup");
        check(smoke.contains("overlay session dismissed before decision controls could be inspected"),
                "emulator smoke must report premature overlay dismissal distinctly from a missing control");
        check(smoke.contains("content-desc=\"JARVIS APPROVE action\""),
                "emulator smoke must prove APPROVE exists in the real VoiceInteractionSession UI tree");
        check(smoke.contains("content-desc=\"JARVIS CANCEL action\""),
                "emulator smoke must prove CANCEL exists in the real VoiceInteractionSession UI tree");
        check(smoke.contains("jarvis-overlay-cancel-tap.txt"),
                "emulator smoke must tap the overlay CANCEL node by accessibility bounds");
        check(smoke.contains("JARVIS_SHARED_BRAIN_ACTIVE.*state=IDLE"),
                "emulator smoke must prove overlay cancellation clears the shared runtime decision");

        System.out.println("AndroidVoiceSessionDecisionAffordanceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
