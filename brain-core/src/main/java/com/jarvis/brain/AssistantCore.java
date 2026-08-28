package com.jarvis.brain;

import java.util.ArrayDeque;
import java.util.Deque;

public final class AssistantCore {
    private static final int MAX_DIALOGUE_MESSAGES = 12;
    private final BrainEngine reflex;
    private final ReasoningRouter providers;
    private final ToolRegistry tools;
    private final PlanValidator planValidator;
    private final AssistantContextSource contextSource;
    private final Deque<String> dialogue = new ArrayDeque<>();

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
        if (response.kind() == BrainResponse.Kind.IGNORED_AMBIENT) return response;
        remember("USER", utterance);

        if (response.kind() != BrainResponse.Kind.REASONING_REQUIRED) {
            remember("JARVIS", response.text());
            return response;
        }

        String durableContext = contextSource.contextFor(utterance);
        String combinedContext = combineContext(dialogueSnapshot(), durableContext);
        ReasoningResult reasoned = providers.reason(new ReasoningRequest(
                utterance,
                combinedContext,
                tools.specs()
        ));
        BrainResponse result;
        if (reasoned.plan() != null) {
            PlanValidation validation = planValidator.validate(reasoned.plan());
            if (!validation.valid()) {
                String details = validation.errors().isEmpty() ? "the plan is incomplete" : String.join("; ", validation.errors());
                result = BrainResponse.of(BrainResponse.Kind.CONVERSATION,
                        "I need clarification before I can build a safe executable plan: " + details + ".",
                        null, response.sessionActive(), response.acceptedWithoutWakeWord(), combinedContext);
            } else {
                result = BrainResponse.of(BrainResponse.Kind.ACTION_PLAN, reasoned.text(), validation.effectivePlan(),
                        response.sessionActive(), response.acceptedWithoutWakeWord(), combinedContext);
            }
        } else {
            result = BrainResponse.of(BrainResponse.Kind.CONVERSATION, reasoned.text(), null,
                    response.sessionActive(), response.acceptedWithoutWakeWord(), combinedContext);
        }
        remember("JARVIS", result.text());
        return result;
    }

    private void remember(String role, String text) {
        if (text == null || text.isBlank()) return;
        dialogue.addLast(role + ": " + text.trim());
        while (dialogue.size() > MAX_DIALOGUE_MESSAGES) dialogue.removeFirst();
    }

    private String dialogueSnapshot() {
        return String.join("\n", dialogue);
    }

    private static String combineContext(String sessionContext, String durableContext) {
        String session = sessionContext == null ? "" : sessionContext.trim();
        String durable = durableContext == null ? "" : durableContext.trim();
        if (session.isEmpty()) return durable;
        if (durable.isEmpty()) return session;
        return "Recent conversation:\n" + session + "\nRelevant durable memory:\n" + durable;
    }
}
