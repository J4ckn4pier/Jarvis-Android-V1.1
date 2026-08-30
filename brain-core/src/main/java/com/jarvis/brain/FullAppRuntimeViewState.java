package com.jarvis.brain;

/**
 * Platform-neutral view model for the full application surface. It deliberately preserves
 * RuntimeSurfacePresentation actions instead of inventing a second approval/recovery dialect.
 */
public record FullAppRuntimeViewState(
        AssistantSurfaceState state,
        String text,
        String detail,
        RuntimeSurfaceAction primaryAction,
        RuntimeSurfaceAction secondaryAction,
        boolean primaryEnabled,
        boolean secondaryEnabled) {

    public FullAppRuntimeViewState {
        if (state == null) throw new IllegalArgumentException("state required");
        text = text == null ? "" : text.trim();
        detail = detail == null ? "" : detail.trim();
        primaryAction = primaryAction == null ? RuntimeSurfaceAction.NONE : primaryAction;
        secondaryAction = secondaryAction == null ? RuntimeSurfaceAction.NONE : secondaryAction;
    }

    public static FullAppRuntimeViewState from(RuntimeSurfacePresentation presentation) {
        if (presentation == null) throw new IllegalArgumentException("presentation required");
        RuntimeSurfaceAction primary = presentation.primaryAction();
        RuntimeSurfaceAction secondary = presentation.secondaryAction();
        return new FullAppRuntimeViewState(
                presentation.state(),
                presentation.text(),
                presentation.detail(),
                primary,
                secondary,
                primary != RuntimeSurfaceAction.NONE,
                secondary != RuntimeSurfaceAction.NONE);
    }
}
