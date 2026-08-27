package com.jarvis.mobile.brain.providers;

import com.jarvis.mobile.brain.core.IntentPlan;

public interface CortexProvider {
    String id();
    boolean isConfigured();
    IntentPlan propose(String utterance) throws Exception;
}
