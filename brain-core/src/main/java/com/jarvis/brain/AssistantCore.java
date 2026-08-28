package com.jarvis.brain;

public final class AssistantCore {
    private final BrainEngine reflex;
    private final ReasoningRouter providers;
    private final ToolRegistry tools;
    private final PlanValidator planValidator;
    private final AssistantContextSource contextSource;

    public AssistantCore(BrainEngine reflex, ProviderRouter providers) {
        this(reflex, providers, ToolRegistry.standard(), AssistantContextSource.none());
    }

    public AssistantCore(BrainEngine reflex, ReasoningRouter providers, ToolRegistry tools) {
        this(reflex, providers, tools, AssistantContextSource.none());
    }

    public AssistantCore(BrainEngine reflex, ReasoningRouter providers, ToolRegistry tools,
                         AssistantContextSource contextSource) {
        if (reflex == null) throw new IllegalArgumentException("brain engine required");
        if (providers == null) throw new IllegalArgumentException("reasoning router required");
        this.reflex = reflex;
        this.providers = providers;
        this.tools = tools == null ? ToolRegistry.standard() : tools;
        this.planValidator = new PlanValidator(this.tools);
        this.contextSource = contextSource == null ? AssistantContextSource.none() : contextSource;
    }

    public BrainResponse handle(String utterance) {
        BrainResponse response = reflex.handle(utterance);
        if (response.kind() != BrainResponse.Kind.REASONING_REQUIRED) return response;
        String durableContext = contextSource.contextFor(utterance);
        String combinedContext = combineContext(response.contextSnapshot(), durableContext);
        ReasoningResult reasoned = providers.reason(new ReasoningRequest(
                utterance,
                combinedContext,
                tools.specs()
        ));
        if (reasoned.plan() != null) {
            PlanValidation validation = planValidator.validate(reasoned.plan());
            if (!validation.valid()) {
                String details = validation.errors().isEmpty() ? "the plan is incomplete" : String.join("; ", validation.errors());
                return BrainResponse.of(BrainResponse.Kind.CONVERSATION,
                        "I need clarification before I can build a safe executable plan: " + details + ".",
                        null, response.sessionActive(), response.acceptedWithoutWakeWord(), combinedContext);
            }
            return BrainResponse.of(BrainResponse.Kind.ACTION_PLAN, reasoned.text(), validation.effectivePlan(),
                    response.sessionActive(), response.acceptedWithoutWakeWord(), combinedContext);
        }
        return BrainResponse.of(BrainResponse.Kind.CONVERSATION, reasoned.text(), null,
                response.sessionActive(), response.acceptedWithoutWakeWord(), combinedContext);
    }

    private static String combineContext(String sessionContext, String durableContext) {
        String session = sessionContext == null ? "" : sessionContext.trim();
        String durable = durableContext == null ? "" : durableContext.trim();
        if (session.isEmpty()) return durable;
        if (durable.isEmpty()) return session;
        return "Recent conversation:\n" + session + "\nRelevant durable memory:\n" + durable;
    }
}
