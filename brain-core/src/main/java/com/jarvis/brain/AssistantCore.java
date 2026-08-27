package com.jarvis.brain;

import java.util.List;

public final class AssistantCore {
    private final BrainEngine reflex;
    private final ProviderRouter providers;

    public AssistantCore(BrainEngine reflex, ProviderRouter providers) {
        this.reflex = reflex;
        this.providers = providers;
    }

    public BrainResponse handle(String utterance) {
        BrainResponse response = reflex.handle(utterance);
        if (response.kind() != BrainResponse.Kind.REASONING_REQUIRED) return response;
        ReasoningResult reasoned = providers.reason(new ReasoningRequest(
                utterance,
                response.contextSnapshot(),
                List.of()
        ));
        if (reasoned.plan() != null) {
            return BrainResponse.of(BrainResponse.Kind.ACTION_PLAN, reasoned.text(), reasoned.plan(),
                    response.sessionActive(), response.acceptedWithoutWakeWord(), response.contextSnapshot());
        }
        return BrainResponse.of(BrainResponse.Kind.CONVERSATION, reasoned.text(), null,
                response.sessionActive(), response.acceptedWithoutWakeWord(), response.contextSnapshot());
    }
}
