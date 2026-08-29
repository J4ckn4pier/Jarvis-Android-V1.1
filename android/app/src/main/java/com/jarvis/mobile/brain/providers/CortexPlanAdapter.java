package com.jarvis.mobile.brain.providers;

import com.jarvis.brain.Plan;
import com.jarvis.brain.PlanStep;
import com.jarvis.brain.PlanValidation;
import com.jarvis.brain.PlanValidator;
import com.jarvis.brain.ReasoningResult;
import com.jarvis.brain.ToolRegistry;
import com.jarvis.mobile.brain.core.IntentPlan;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compatibility bridge from the legacy Android provider schema into the shared executive plan.
 * Only lossless mappings are allowed here. Ambiguous or underspecified intents stay actionless
 * until the provider schema can express the typed arguments required by the shared ToolSpec.
 */
public final class CortexPlanAdapter {
    private CortexPlanAdapter() {}

    public static ReasoningResult toReasoningResult(
            CortexProvider provider, IntentPlan proposed, ToolRegistry tools) {
        if (provider == null || proposed == null || !proposed.isResolved()) {
            String id = provider == null ? "cortex" : provider.id();
            return new ReasoningResult(id, "I couldn't resolve that request safely.", null);
        }

        Plan draft = toSharedPlan(proposed);
        if (draft == null) {
            return new ReasoningResult(provider.id(), proposed.answer(), null);
        }

        PlanValidation validation = new PlanValidator(tools).validate(draft);
        Plan plan = validation.valid() ? validation.effectivePlan() : null;
        return new ReasoningResult(provider.id(), proposed.answer(), plan);
    }

    private static Plan toSharedPlan(IntentPlan proposed) {
        PlanStep step = switch (proposed.intent()) {
            case NAVIGATE -> required("navigate", "destination", proposed.payload());
            case FLASHLIGHT_ON -> new PlanStep("set_flashlight", Map.of("state", "on"), false);
            case FLASHLIGHT_OFF -> new PlanStep("set_flashlight", Map.of("state", "off"), false);
            case MEDIA_PLAY -> required("media_play", "query", proposed.payload());
            case NOTIFICATIONS -> new PlanStep("notification_query", Map.of(), false);
            case CALENDAR -> required("calendar_query", "when", proposed.payload());
            case TIMER -> timer(proposed.payload());

            // The legacy schema has one free-form payload and cannot safely represent the shared
            // recipient/message fields. Never infer or split consequential communications here.
            case SMS, EMAIL -> null;

            // CALL/DIAL similarly cannot preserve a contact/number in the current shared dialer
            // contract. Other legacy intents have no lossless shared ToolSpec mapping yet.
            case CALL, DIAL, OPEN_APP, WEB_SEARCH, ALARM, VOLUME_UP, VOLUME_DOWN, MUTE, UNMUTE,
                    MEDIA_PAUSE, MEDIA_NEXT, MEDIA_PREVIOUS, ACCESSIBILITY, REMEMBER, RECALL,
                    ADD_TASK, LIST_TASKS, COMPLETE_TASK, TIME, DATE, BATTERY, HELP, GREETING,
                    IDENTITY, THANKS, CONVERSATION, KNOWLEDGE_QUERY, UNKNOWN -> null;
        };

        if (step == null) return null;
        String goal = proposed.canonicalCommand().isBlank()
                ? proposed.intent().name().toLowerCase(Locale.ROOT)
                : proposed.canonicalCommand();
        return new Plan(goal, List.of(step));
    }

    private static PlanStep required(String tool, String argument, String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isEmpty() ? null : new PlanStep(tool, Map.of(argument, clean), false);
    }

    private static PlanStep timer(String payload) {
        String clean = payload == null ? "" : payload.trim().toLowerCase(Locale.ROOT);
        String[] parts = clean.split("\\s+", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) return null;
        try {
            Double.parseDouble(parts[0]);
        } catch (NumberFormatException invalidAmount) {
            return null;
        }
        return new PlanStep("set_timer", Map.of("amount", parts[0], "unit", parts[1]), false);
    }
}
