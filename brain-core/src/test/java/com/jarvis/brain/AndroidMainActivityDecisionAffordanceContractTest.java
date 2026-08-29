package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Full-app approval/recovery affordances must be real tappable controls, not labels rendered into response text. */
public final class AndroidMainActivityDecisionAffordanceContractTest {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));

        check(source.contains("import android.widget.Button;"),
                "MainActivity must use real Android buttons for runtime decisions");
        check(source.contains("private LinearLayout decisionPanel;"),
                "MainActivity must own a dedicated decision control surface");
        check(source.contains("private Button primaryActionButton;"),
                "MainActivity must expose the shared primary runtime action as a tappable control");
        check(source.contains("private Button secondaryActionButton;"),
                "MainActivity must expose the shared secondary runtime action as a tappable control");
        check(source.contains("applyDecisionActions(view);"),
                "every RuntimeSurfacePresentation projection must refresh decision affordances");
        check(source.contains("case APPROVE: deliverPresentation(runtime.approvePresentation());"),
                "APPROVE must execute the shared pending-approval continuation");
        check(source.contains("case RETRY: deliverPresentation(runtime.retryPresentation());"),
                "RETRY must execute the shared pending-recovery continuation");
        check(source.contains("case CANCEL: deliverPresentation(runtime.cancelPresentation());"),
                "CANCEL must execute the shared cancellation continuation");
        check(!source.contains("if (view.primaryEnabled()) rendered += \"\\n\\n\" + view.primaryAction();"),
                "full app must not represent the primary decision affordance only as response text");
        check(!source.contains("if (view.secondaryEnabled()) rendered += \" / \" + view.secondaryAction();"),
                "full app must not represent the secondary decision affordance only as response text");

        System.out.println("AndroidMainActivityDecisionAffordanceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
