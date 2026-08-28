package com.jarvis.mobile.brain;

import android.content.Context;
import com.jarvis.brain.AssistantCore;
import com.jarvis.brain.BrainEngine;
import com.jarvis.brain.BrainRuntime;
import com.jarvis.brain.ExternalResearchGateway;
import com.jarvis.brain.ReasoningResult;
import com.jarvis.brain.ReasoningRouter;
import com.jarvis.brain.ToolRegistry;
import com.jarvis.mobile.brain.providers.CortexProvider;
import com.jarvis.mobile.brain.providers.CortexProviderFactory;
import java.time.Clock;

/** Android composition root for the shared brain. UI/voice surfaces must use this instead of owning intent logic. */
public final class AndroidBrainRuntime {
    private final BrainRuntime runtime;

    public AndroidBrainRuntime(Context context) {
        Context app = context.getApplicationContext();
        ExternalResearchGateway research = ExternalResearchGateway.unavailable();
        ToolRegistry tools = AndroidToolRegistryFactory.create(app, research);
        ReasoningRouter reasoning = request -> reasonWithConfiguredCortex(app, request.utterance());
        AssistantCore assistant = new AssistantCore(BrainEngine.createDefault(Clock.systemDefaultZone()), reasoning, tools);
        runtime = new BrainRuntime(assistant, tools);
    }

    public BrainRuntime.Result handle(String utterance) { return runtime.handle(utterance); }
    public BrainRuntime.Result approvePending() { return runtime.approvePending(); }
    public void cancelPending() { runtime.cancelPending(); }
    public boolean hasPendingApproval() { return runtime.hasPendingApproval(); }

    private static ReasoningResult reasonWithConfiguredCortex(Context context, String utterance) {
        CortexProvider provider = CortexProviderFactory.create(context);
        if (!provider.isConfigured() || "local".equals(provider.id())) {
            return new ReasoningResult("local", "I need a connected reasoning cortex for that request.", null);
        }
        try {
            com.jarvis.mobile.brain.core.IntentPlan proposed = provider.propose(utterance);
            if (proposed == null || !proposed.isResolved()) {
                return new ReasoningResult(provider.id(), "I couldn't resolve that request safely.", null);
            }
            // Provider prose is allowed here; Android actions remain exclusively behind shared ToolRegistry plans.
            return new ReasoningResult(provider.id(), proposed.answer(), null);
        } catch (Exception failure) {
            return new ReasoningResult(provider.id(), "The optional reasoning cortex is unavailable right now.", null);
        }
    }
}
