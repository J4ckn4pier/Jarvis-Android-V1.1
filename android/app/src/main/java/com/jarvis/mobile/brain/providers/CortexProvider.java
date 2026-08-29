package com.jarvis.mobile.brain.providers;

import com.jarvis.brain.ReasoningRequest;
import com.jarvis.brain.ReasoningResult;
import com.jarvis.brain.ToolRegistry;
import com.jarvis.mobile.brain.core.IntentPlan;

public interface CortexProvider {
    String id();
    boolean isConfigured();
    IntentPlan propose(String utterance) throws Exception;

    /**
     * Shared executive path. Legacy providers inherit a conservative compatibility bridge until
     * they implement the typed shared-plan schema directly.
     */
    default ReasoningResult proposeReasoning(ReasoningRequest request, ToolRegistry tools) throws Exception {
        return CortexPlanAdapter.toReasoningResult(this, propose(request.utterance()), tools);
    }
}
