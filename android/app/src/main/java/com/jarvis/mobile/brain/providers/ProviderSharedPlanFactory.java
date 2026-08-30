package com.jarvis.mobile.brain.providers;

import com.jarvis.brain.Plan;
import com.jarvis.brain.PlanStep;
import com.jarvis.brain.PlanValidation;
import com.jarvis.brain.PlanValidator;
import com.jarvis.brain.ReasoningResult;
import com.jarvis.brain.ToolRegistry;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts schema-bounded provider JSON into the shared executive plan and policy validator. */
public final class ProviderSharedPlanFactory {
    private ProviderSharedPlanFactory() {}

    public static ReasoningResult fromJson(String providerId, JSONObject json, ToolRegistry tools) {
        if (json == null) return new ReasoningResult(providerId, "", null);
        String answer = json.optString("answer", "").trim();
        String goal = json.optString("goal", "").trim();
        JSONArray rawSteps = json.optJSONArray("steps");
        if (rawSteps == null || rawSteps.length() == 0) {
            return new ReasoningResult(providerId, answer, null);
        }

        List<PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < rawSteps.length(); i++) {
            JSONObject rawStep = rawSteps.optJSONObject(i);
            if (rawStep == null) return new ReasoningResult(providerId, answer, null);
            String tool = rawStep.optString("tool", "").trim();
            if (tool.isEmpty()) return new ReasoningResult(providerId, answer, null);

            Map<String, String> arguments = new LinkedHashMap<>();
            JSONArray rawArguments = rawStep.optJSONArray("arguments");
            if (rawArguments == null) return new ReasoningResult(providerId, answer, null);
            for (int j = 0; j < rawArguments.length(); j++) {
                JSONObject rawArgument = rawArguments.optJSONObject(j);
                if (rawArgument == null) return new ReasoningResult(providerId, answer, null);
                String key = rawArgument.optString("key", "").trim();
                String value = rawArgument.optString("value", "");
                if (key.isEmpty() || arguments.containsKey(key)) {
                    return new ReasoningResult(providerId, answer, null);
                }
                arguments.put(key, value);
            }
            steps.add(new PlanStep(tool, Map.copyOf(arguments), false));
        }

        Plan draft = new Plan(goal.isEmpty() ? "provider-plan" : goal, List.copyOf(steps));
        PlanValidation validation = new PlanValidator(tools).validate(draft);
        return new ReasoningResult(providerId, answer,
                validation.valid() ? validation.effectivePlan() : null);
    }
}
