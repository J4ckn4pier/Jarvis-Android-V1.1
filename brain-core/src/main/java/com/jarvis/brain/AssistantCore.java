package com.jarvis.brain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AssistantCore {
    private static final int MAX_DIALOGUE_MESSAGES = 12;
    private final BrainEngine reflex;
    private final ReasoningRouter providers;
    private final ToolRegistry tools;
    private final PlanValidator planValidator;
    private final AssistantContextSource contextSource;
    private final SemanticGoalInterpreter semanticReflex = new SemanticGoalInterpreter();
    private final Deque<String> dialogue = new ArrayDeque<>();
    private final ConversationWorkingMemory workingMemory = new ConversationWorkingMemory();
    /** Structured executive state. Deliberately independent from the lossy dialogue buffer. */
    private PendingClarification pendingClarification;

    public AssistantCore(BrainEngine reflex, ProviderRouter providers) { this(reflex, providers, ToolRegistry.standard(), AssistantContextSource.none()); }
    public AssistantCore(BrainEngine reflex, ReasoningRouter providers, ToolRegistry tools) { this(reflex, providers, tools, AssistantContextSource.none()); }
    public AssistantCore(BrainEngine reflex, ReasoningRouter providers, ToolRegistry tools, AssistantContextSource contextSource) {
        if (reflex == null) throw new IllegalArgumentException("brain engine required");
        if (providers == null) throw new IllegalArgumentException("reasoning router required");
        this.reflex = reflex; this.providers = providers;
        this.tools = tools == null ? ToolRegistry.standard() : tools;
        this.planValidator = new PlanValidator(this.tools);
        this.contextSource = contextSource == null ? AssistantContextSource.none() : contextSource;
    }

    public boolean hasPendingPlan() { return pendingClarification != null; }
    public String pendingPlanGoal() { return pendingClarification == null ? "" : pendingClarification.plan().goal(); }
    void noteDialogueForTest(String role, String text) { remember(role, text); }

    public BrainResponse handle(String utterance) {
        if (pendingClarification != null) {
            BrainResponse resumed = handlePendingClarification(utterance);
            if (resumed != null) return resumed;
        }
        BrainResponse response = reflex.handle(utterance);
        if (response.kind() == BrainResponse.Kind.IGNORED_AMBIENT) return response;
        workingMemory.observeUserTurn(utterance);
        remember("USER", utterance);
        if (response.kind() != BrainResponse.Kind.REASONING_REQUIRED) { remember("JARVIS", response.text()); return response; }

        String durableContext = contextSource.contextFor(utterance);
        String combinedContext = combineContext(dialogueSnapshot(), workingMemory.snapshot(), durableContext);

        Plan semanticPlan = semanticReflex.interpret(utterance).orElse(null);
        if (semanticPlan != null) {
            BrainResponse semanticResponse = validatedPlanResponse(semanticPlan,
                    "Understood.", response.sessionActive(), response.acceptedWithoutWakeWord(), combinedContext);
            remember("JARVIS", semanticResponse.text());
            return semanticResponse;
        }

        ReasoningResult reasoned = providers.reason(new ReasoningRequest(utterance, combinedContext, tools.specs()));
        BrainResponse result;
        if (reasoned.plan() != null) {
            result = validatedPlanResponse(reasoned.plan(), reasoned.text(), response.sessionActive(),
                    response.acceptedWithoutWakeWord(), combinedContext);
        } else result = BrainResponse.of(BrainResponse.Kind.CONVERSATION, reasoned.text(), null,
                response.sessionActive(), response.acceptedWithoutWakeWord(), combinedContext);
        remember("JARVIS", result.text());
        return result;
    }

    private BrainResponse validatedPlanResponse(Plan plan, String successText, boolean sessionActive,
                                                boolean acceptedWithoutWake, String context) {
        PlanValidation validation = planValidator.validate(plan);
        if (!validation.valid()) {
            List<PendingClarification.MissingArgument> missing = findMissingArguments(validation.effectivePlan());
            if (!missing.isEmpty()) {
                pendingClarification = new PendingClarification(validation.effectivePlan(), missing);
                return BrainResponse.of(BrainResponse.Kind.CONVERSATION,
                        clarificationQuestion(missing.get(0).argument()), null,
                        sessionActive, acceptedWithoutWake, context);
            }
            String details = validation.errors().isEmpty() ? "the plan is incomplete" : String.join("; ", validation.errors());
            return BrainResponse.of(BrainResponse.Kind.CONVERSATION,
                    "I need clarification before I can build a safe executable plan: " + details + ".",
                    null, sessionActive, acceptedWithoutWake, context);
        }
        return BrainResponse.of(BrainResponse.Kind.ACTION_PLAN,
                successText == null || successText.isBlank() ? "Understood." : successText,
                validation.effectivePlan(), sessionActive, acceptedWithoutWake, context);
    }

    private BrainResponse handlePendingClarification(String utterance) {
        String answer = utterance == null ? "" : utterance.trim();
        if (answer.isEmpty()) return null;
        String lower = answer.toLowerCase(Locale.ROOT);
        if (lower.matches("cancel|never mind|nevermind|forget it|stop")) {
            pendingClarification = null; remember("USER", answer); remember("JARVIS", "Cancelled.");
            return BrainResponse.of(BrainResponse.Kind.CONVERSATION, "Cancelled.", null, true, true, dialogueSnapshot());
        }
        PendingClarification state = pendingClarification;
        PendingClarification.MissingArgument target = state.missing().get(0);
        Plan filled = fillArgument(state.plan(), target, answer);
        // Clarification never bypasses safety: it re-enters the exact same registry-backed validator path.
        PlanValidation validation = planValidator.validate(filled);
        List<PendingClarification.MissingArgument> remaining = findMissingArguments(validation.effectivePlan());
        workingMemory.observeUserTurn(answer);
        remember("USER", answer);
        if (validation.valid()) {
            pendingClarification = null;
            BrainResponse result = BrainResponse.of(BrainResponse.Kind.ACTION_PLAN, "Understood. I have what I need.",
                    validation.effectivePlan(), true, true, combinedSessionContext());
            remember("JARVIS", result.text()); return result;
        }
        if (!remaining.isEmpty()) {
            pendingClarification = new PendingClarification(validation.effectivePlan(), remaining);
            String question = clarificationQuestion(remaining.get(0).argument()); remember("JARVIS", question);
            return BrainResponse.of(BrainResponse.Kind.CONVERSATION, question, null, true, true, combinedSessionContext());
        }
        pendingClarification = null;
        String details = validation.errors().isEmpty() ? "the plan is still incomplete" : String.join("; ", validation.errors());
        String text = "I still can't make that plan safe to execute: " + details + "."; remember("JARVIS", text);
        return BrainResponse.of(BrainResponse.Kind.CONVERSATION, text, null, true, true, combinedSessionContext());
    }

    private List<PendingClarification.MissingArgument> findMissingArguments(Plan plan) {
        List<PendingClarification.MissingArgument> missing = new ArrayList<>(); if (plan == null) return missing;
        for (int i = 0; i < plan.steps().size(); i++) {
            PlanStep step = plan.steps().get(i); ToolRegistry.RegisteredTool registered = tools.resolve(step.tool()).orElse(null);
            if (registered == null) continue;
            for (String required : registered.spec().requiredArguments()) {
                String value = step.arguments().get(required);
                if (value == null || value.isBlank()) missing.add(new PendingClarification.MissingArgument(i, required));
            }
        }
        return List.copyOf(missing);
    }
    private static Plan fillArgument(Plan plan, PendingClarification.MissingArgument target, String value) {
        List<PlanStep> steps = new ArrayList<>(plan.steps()); PlanStep old = steps.get(target.stepIndex());
        Map<String,String> args = new HashMap<>(old.arguments()); args.put(target.argument(), value.trim());
        steps.set(target.stepIndex(), new PlanStep(old.tool(), Map.copyOf(args), old.consequential()));
        return new Plan(plan.goal(), List.copyOf(steps));
    }
    private static String clarificationQuestion(String argument) {
        return switch (argument) {
            case "destination" -> "Where would you like to go?"; case "recipient" -> "Who should I send it to?";
            case "message" -> "What would you like me to say?"; case "business" -> "Which business do you mean?";
            case "when" -> "When should I do that?"; case "amount" -> "How long?";
            case "unit" -> "Seconds, minutes, or hours?"; default -> "What should I use for " + argument.replace('_',' ') + "?";
        };
    }
    private void remember(String role, String text) {
        if (text == null || text.isBlank()) return; dialogue.addLast(role + ": " + text.trim());
        while (dialogue.size() > MAX_DIALOGUE_MESSAGES) dialogue.removeFirst();
    }
    private String dialogueSnapshot() { return String.join("\n", dialogue); }
    private String combinedSessionContext() { return combineContext(dialogueSnapshot(), workingMemory.snapshot(), ""); }
    private static String combineContext(String dialogueContext, String workingContext, String durableContext) {
        String dialogue = dialogueContext == null ? "" : dialogueContext.trim();
        String working = workingContext == null ? "" : workingContext.trim();
        String durable = durableContext == null ? "" : durableContext.trim();
        StringBuilder out = new StringBuilder();
        if (!dialogue.isEmpty()) out.append("Recent conversation:\n").append(dialogue);
        if (!working.isEmpty()) {
            if (out.length() > 0) out.append('\n');
            out.append("Structured session memory:\n").append(working);
        }
        if (!durable.isEmpty()) {
            if (out.length() > 0) out.append('\n');
            out.append("Relevant durable memory:\n").append(durable);
        }
        return out.toString();
    }
}
