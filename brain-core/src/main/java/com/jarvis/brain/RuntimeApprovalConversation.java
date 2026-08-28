package com.jarvis.brain;

import java.util.Locale;

/** Converts short affirmative/deferral replies into the pending BrainRuntime approval path. */
public final class RuntimeApprovalConversation {
    private final BrainRuntime runtime;

    public RuntimeApprovalConversation(BrainRuntime runtime) {
        if (runtime == null) throw new IllegalArgumentException("runtime required");
        this.runtime = runtime;
    }

    public synchronized RuntimeSurfacePresentation handle(String utterance) {
        String normalized = utterance == null ? "" : utterance.trim().toLowerCase(Locale.ROOT);
        if (runtime.hasPendingApproval()) {
            if (isApproval(normalized)) {
                return RuntimeSurfacePresentation.from(runtime.approvePending());
            }
            if (isDeferral(normalized)) {
                runtime.cancelPending();
                return new RuntimeSurfacePresentation(
                        AssistantSurfaceState.IDLE,
                        "Not yet.",
                        "Pending action cancelled",
                        RuntimeSurfaceAction.NONE,
                        RuntimeSurfaceAction.NONE);
            }
        }
        return RuntimeSurfacePresentation.from(runtime.handle(utterance));
    }

    public synchronized RuntimeSurfacePresentation approvePending() {
        return RuntimeSurfacePresentation.from(runtime.approvePending());
    }

    public synchronized RuntimeSurfacePresentation cancelPending() {
        runtime.cancelPending();
        return new RuntimeSurfacePresentation(
                AssistantSurfaceState.IDLE,
                "Cancelled.",
                "Pending action cancelled",
                RuntimeSurfaceAction.NONE,
                RuntimeSurfaceAction.NONE);
    }

    public synchronized boolean hasPendingApproval() {
        return runtime.hasPendingApproval();
    }

    private static boolean isApproval(String value) {
        return value.equals("yes") || value.equals("yes please") || value.equals("do it")
                || value.equals("go ahead") || value.equals("send it") || value.equals("confirm");
    }

    private static boolean isDeferral(String value) {
        return value.equals("no") || value.equals("not yet") || value.equals("cancel")
                || value.equals("never mind") || value.equals("nevermind") || value.equals("later");
    }
}
