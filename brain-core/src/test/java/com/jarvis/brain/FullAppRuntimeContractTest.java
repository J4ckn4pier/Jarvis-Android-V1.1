package com.jarvis.brain;

public final class FullAppRuntimeContractTest {
    public static void main(String[] args) {
        RuntimeSurfacePresentation done = RuntimeSurfacePresentation.from(
                new BrainRuntime.Result(BrainRuntime.Status.COMPLETED, "Done", "", java.util.List.of("Done")));
        FullAppRuntimeViewState doneView = FullAppRuntimeViewState.from(done);
        check(doneView.state() == AssistantSurfaceState.ACTION_DONE, "completed state retained");
        check(!doneView.primaryEnabled() && !doneView.secondaryEnabled(), "completed has no decision buttons");

        RuntimeSurfacePresentation approval = RuntimeSurfacePresentation.from(
                new BrainRuntime.Result(BrainRuntime.Status.APPROVAL_REQUIRED, "Send this?", "send_message", java.util.List.of()));
        FullAppRuntimeViewState approvalView = FullAppRuntimeViewState.from(approval);
        check(approvalView.primaryAction() == RuntimeSurfaceAction.APPROVE, "approval action retained");
        check(approvalView.secondaryAction() == RuntimeSurfaceAction.CANCEL, "approval cancel retained");
        check(approvalView.primaryEnabled() && approvalView.secondaryEnabled(), "approval buttons enabled");

        RuntimeSurfacePresentation recovery = RuntimeSurfacePresentation.from(
                new BrainRuntime.Result(BrainRuntime.Status.RECOVERY_REQUIRED, "Choose another option", "parking_search", java.util.List.of()));
        FullAppRuntimeViewState recoveryView = FullAppRuntimeViewState.from(recovery);
        check(recoveryView.primaryAction() == RuntimeSurfaceAction.RETRY, "recovery is retry not approve");
        check(recoveryView.secondaryAction() == RuntimeSurfaceAction.CANCEL, "recovery can cancel");
        System.out.println("FullAppRuntimeContractTest passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
