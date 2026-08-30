package com.jarvis.brain;

import java.util.List;

public final class RuntimeSurfacePresentationTest {
    private static int checks;
    public static void main(String[] args) {
        approvalIsExplicitAndActionable();
        recoveryAndFailureRemainVisible();
        completedAndIgnoredAreDistinct();
        System.out.println("RuntimeSurfacePresentationTest: " + checks + " assertions passed");
    }

    private static void approvalIsExplicitAndActionable() {
        BrainRuntime.Result runtime = new BrainRuntime.Result(BrainRuntime.Status.APPROVAL_REQUIRED,
                "I need approval before I send that.", "send_message", List.of());
        RuntimeSurfacePresentation p = RuntimeSurfacePresentation.from(runtime);
        check(p.state() == AssistantSurfaceState.AWAITING_APPROVAL, "approval projects explicit state");
        check(p.primaryAction() == RuntimeSurfaceAction.APPROVE, "approval exposes approve");
        check(p.secondaryAction() == RuntimeSurfaceAction.CANCEL, "approval exposes cancel");
        check(p.detail().contains("send_message"), "blocked tool remains visible");
    }

    private static void recoveryAndFailureRemainVisible() {
        RuntimeSurfacePresentation recovery = RuntimeSurfacePresentation.from(new BrainRuntime.Result(
                BrainRuntime.Status.RECOVERY_REQUIRED, "Network unavailable", "weather_lookup", List.of()));
        check(recovery.state() == AssistantSurfaceState.NEEDS_INPUT, "recovery becomes needs-input");
        check(recovery.primaryAction() == RuntimeSurfaceAction.RETRY, "recovery offers retry");

        RuntimeSurfacePresentation failed = RuntimeSurfacePresentation.from(new BrainRuntime.Result(
                BrainRuntime.Status.FAILED, "Action failed safely", "", List.of()));
        check(failed.state() == AssistantSurfaceState.ERROR, "failure explicit");
        check(failed.primaryAction() == RuntimeSurfaceAction.NONE, "failure does not invent action");
    }

    private static void completedAndIgnoredAreDistinct() {
        RuntimeSurfacePresentation done = RuntimeSurfacePresentation.from(new BrainRuntime.Result(
                BrainRuntime.Status.COMPLETED, "Done", "", List.of("Done")));
        check(done.state() == AssistantSurfaceState.ACTION_DONE, "completion projects done");
        RuntimeSurfacePresentation ignored = RuntimeSurfacePresentation.from(new BrainRuntime.Result(
                BrainRuntime.Status.IGNORED, "", "", List.of()));
        check(ignored.state() == AssistantSurfaceState.IDLE, "ambient ignored returns idle");
    }

    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
