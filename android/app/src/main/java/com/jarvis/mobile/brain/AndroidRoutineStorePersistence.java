package com.jarvis.mobile.brain;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.jarvis.brain.Plan;
import com.jarvis.brain.PlanStep;
import com.jarvis.brain.RoutineDefinition;
import com.jarvis.brain.RoutineStorePersistence;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** App-private persistence for user-authored routines. Credential material does not belong here. */
public final class AndroidRoutineStorePersistence implements RoutineStorePersistence {
    private static final String NAME = "jarvis_routines";
    private final SharedPreferences preferences;

    public AndroidRoutineStorePersistence(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        preferences = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    @Override
    public Map<String,RoutineDefinition> load() {
        Map<String,RoutineDefinition> restored = new LinkedHashMap<>();
        for (Map.Entry<String,?> entry : preferences.getAll().entrySet()) {
            if (!(entry.getValue() instanceof String encoded)) continue;
            RoutineDefinition routine = decode(encoded);
            if (routine != null) restored.put(routine.id(), routine);
        }
        return Map.copyOf(restored);
    }

    @Override
    public void put(RoutineDefinition routine) {
        if (routine == null) throw new IllegalArgumentException("routine required");
        preferences.edit().putString(key(routine.id()), encode(routine)).apply();
    }

    @Override
    public void remove(String id) {
        preferences.edit().remove(key(id)).apply();
    }

    private static String key(String id) {
        String clean = id == null ? "" : id.trim();
        if (clean.isBlank()) throw new IllegalArgumentException("id required");
        return Base64.encodeToString(clean.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String encode(RoutineDefinition routine) {
        try {
            JSONObject triggerArguments = mapToJson(routine.triggerArguments());
            JSONArray steps = new JSONArray();
            for (PlanStep step : routine.actionPlan().steps()) {
                JSONObject encodedStep = new JSONObject();
                encodedStep.put("tool", step.tool());
                encodedStep.put("arguments", mapToJson(step.arguments()));
                encodedStep.put("consequential", step.consequential());
                steps.put(encodedStep);
            }
            JSONObject actionPlan = new JSONObject();
            actionPlan.put("goal", routine.actionPlan().goal());
            actionPlan.put("steps", steps);

            JSONObject value = new JSONObject();
            value.put("id", routine.id());
            value.put("title", routine.title());
            value.put("triggerType", routine.triggerType());
            value.put("triggerArguments", triggerArguments);
            value.put("actionPlan", actionPlan);
            value.put("enabled", routine.enabled());
            return value.toString();
        } catch (JSONException failure) {
            throw new IllegalStateException("Unable to serialize routine", failure);
        }
    }

    private static RoutineDefinition decode(String encoded) {
        try {
            JSONObject value = new JSONObject(encoded);
            JSONObject actionPlanJson = value.getJSONObject("actionPlan");
            JSONArray stepsJson = actionPlanJson.getJSONArray("steps");
            List<PlanStep> steps = new ArrayList<>();
            for (int index = 0; index < stepsJson.length(); index++) {
                JSONObject step = stepsJson.getJSONObject(index);
                steps.add(new PlanStep(
                        step.getString("tool"),
                        jsonToMap(step.optJSONObject("arguments")),
                        step.getBoolean("consequential")));
            }
            Plan actionPlan = new Plan(actionPlanJson.getString("goal"), List.copyOf(steps));
            return new RoutineDefinition(
                    value.getString("id"),
                    value.getString("title"),
                    value.getString("triggerType"),
                    jsonToMap(value.optJSONObject("triggerArguments")),
                    actionPlan,
                    value.optBoolean("enabled", true));
        } catch (JSONException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static JSONObject mapToJson(Map<String,String> values) throws JSONException {
        JSONObject result = new JSONObject();
        if (values == null) return result;
        for (Map.Entry<String,String> entry : values.entrySet()) result.put(entry.getKey(), entry.getValue());
        return result;
    }

    private static Map<String,String> jsonToMap(JSONObject value) {
        if (value == null) return Map.of();
        Map<String,String> result = new LinkedHashMap<>();
        Iterator<String> keys = value.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String item = value.optString(key, null);
            if (item != null) result.put(key, item);
        }
        return Map.copyOf(result);
    }
}
