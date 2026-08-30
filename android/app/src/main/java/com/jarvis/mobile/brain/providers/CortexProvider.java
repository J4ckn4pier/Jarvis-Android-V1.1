package com.jarvis.mobile.brain.providers;

import com.jarvis.brain.ReasoningRequest;
import com.jarvis.brain.ReasoningResult;
import com.jarvis.brain.ToolRegistry;

/** Provider-neutral Android reasoning cortex. Providers propose shared typed plans; they never execute actions. */
public interface CortexProvider {
    String id();
    boolean isConfigured();
    ReasoningResult proposeReasoning(ReasoningRequest request, ToolRegistry tools) throws Exception;
}
