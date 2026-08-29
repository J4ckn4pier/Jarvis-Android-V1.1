package com.jarvis.mobile.brain;

import android.content.Context;
import android.util.Log;
import com.jarvis.brain.*;
import com.jarvis.mobile.brain.providers.CortexPlanAdapter;
import com.jarvis.mobile.brain.providers.CortexProvider;
import com.jarvis.mobile.brain.providers.CortexProviderFactory;
import java.time.Clock;

/** Android composition root for the shared brain. UI/voice surfaces must use this instead of owning intent logic. */
public final class AndroidBrainRuntime {
    private static final String TRACE_TAG = "JARVIS_COMMAND_TRACE";
    private final BrainRuntime runtime;
    private final RuntimeApprovalConversation conversation;

    public AndroidBrainRuntime(Context context) {
        Context app = context.getApplicationContext();
        ExternalResearchGateway research = ExternalResearchGateway.unavailable();
        ToolRegistry tools = AndroidToolRegistryFactory.create(app, research);
        ReasoningRouter reasoning = request -> reasonWithConfiguredCortex(app, request, tools);
        BrainEngine brain = BrainEngine.createDefault(Clock.systemDefaultZone());
        brain.beginInvokedConversation();
        AssistantCore assistant = new AssistantCore(brain, reasoning, tools);
        runtime = new BrainRuntime(assistant, tools);
        conversation = new RuntimeApprovalConversation(runtime);
    }

    public BrainRuntime.Result handle(String utterance) { return runtime.handle(utterance); }
    public BrainRuntime.Result approvePending() { return runtime.approvePending(); }
    public BrainRuntime.Result retryPending() { return runtime.retryPending(); }
    public void cancelPending() { runtime.cancelPending(); }
    public boolean hasPendingApproval() { return runtime.hasPendingApproval(); }
    public boolean hasPendingRecovery() { return runtime.hasPendingRecovery(); }

    public RuntimeSurfacePresentation handlePresentation(String utterance) {
        Log.i(TRACE_TAG, "JARVIS_RUNTIME_INPUT utterance=" + String.valueOf(utterance));
        RuntimeSurfacePresentation presentation = conversation.handle(utterance);
        Log.i(TRACE_TAG, "JARVIS_RUNTIME_OUTPUT state=" + presentation.state() + " text=" + presentation.text());
        return presentation;
    }

    public RuntimeSurfacePresentation approvePresentation() { return conversation.approvePending(); }
    public RuntimeSurfacePresentation retryPresentation() { return conversation.retryPending(); }
    public RuntimeSurfacePresentation cancelPresentation() { return conversation.cancelPending(); }

    private static ReasoningResult reasonWithConfiguredCortex(
            Context context, ReasoningRequest request, ToolRegistry tools) {
        CortexProvider provider = CortexProviderFactory.create(context);
        if (!provider.isConfigured() || "local".equals(provider.id())) {
            return new ReasoningResult("local", "I need a connected reasoning cortex for that request.", null);
        }
        try {
            com.jarvis.mobile.brain.core.IntentPlan proposed = provider.propose(request.utterance());
            return CortexPlanAdapter.toReasoningResult(provider, proposed, tools);
        } catch (Exception failure) {
            return new ReasoningResult(provider.id(), "The optional reasoning cortex is unavailable right now.", null);
        }
    }
}
