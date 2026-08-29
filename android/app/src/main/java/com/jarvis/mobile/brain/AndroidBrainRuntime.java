package com.jarvis.mobile.brain;

import android.content.Context;
import android.util.Log;
import com.jarvis.brain.*;
import com.jarvis.mobile.brain.providers.CortexProvider;
import com.jarvis.mobile.brain.providers.CortexProviderFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Android composition root for the shared brain. UI/voice surfaces must use this instead of owning intent logic. */
public final class AndroidBrainRuntime {
    private static final String TRACE_TAG = "JARVIS_COMMAND_TRACE";
    private final BrainRuntime runtime;
    private final RuntimeApprovalConversation conversation;
    private final SettingsStore settings;
    private final OutcomeFollowupRuntime followups;
    private final JarvisUiBackend uiBackend;

    public AndroidBrainRuntime(Context context) {
        Context app = context.getApplicationContext();
        ExternalResearchGateway research = ExternalResearchGateway.unavailable();
        ToolRegistry tools = AndroidToolRegistryFactory.create(app, research);
        ConnectionRegistry connections = new ConnectionRegistry();
        ReasoningRouter reasoning = request -> reasonWithConfiguredCortex(app, request, tools);
        BrainEngine brain = BrainEngine.createDefault(Clock.systemDefaultZone());
        brain.beginInvokedConversation();
        AssistantCore assistant = new AssistantCore(brain, reasoning, tools);
        runtime = new BrainRuntime(assistant, tools);
        conversation = new RuntimeApprovalConversation(runtime);

        settings = new SettingsStore(new AndroidSharedPreferencesSettingsPersistence(app));
        uiBackend = new JarvisUiBackend(null, tools, connections, settings);
        OutcomeFollowupStore followupStore = new EncryptedFileOutcomeFollowupStore(
                app.getNoBackupFilesDir().toPath().resolve("jarvis").resolve("pending-outcome-followups.bin"),
                new AndroidKeystoreMemoryCipher());
        OutcomeFollowupCoordinator followupCoordinator = new OutcomeFollowupCoordinator(
                new EpisodeFollowupPolicy(Duration.ofMinutes(30)),
                new ProactiveExecutive(new AttentionGate(0.70), Duration.ZERO, true),
                followupStore);
        followups = new OutcomeFollowupRuntime(settings, followupCoordinator);
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

    /** Platform adapters call this only after a recommendation/action has actually been acted on. */
    public void recordActedOnEpisode(RecommendationEpisode episode, Instant actedAt) {
        followups.recordActedOn(episode, actedAt);
    }

    /** Platform adapters report semantic signals only; privacy consent remains backend-owned. */
    public Optional<ProactiveIntervention> onOutcomeFollowupSignal(OutcomeFollowupSignal signal) {
        return followups.onSignal(signal);
    }

    public SettingsStore settings() { return settings; }
    public JarvisUiBackend uiBackend() { return uiBackend; }

    private static ReasoningResult reasonWithConfiguredCortex(
            Context context, ReasoningRequest request, ToolRegistry tools) {
        CortexProvider provider = CortexProviderFactory.create(context);
        if (!provider.isConfigured() || "local".equals(provider.id())) {
            return new ReasoningResult("local", "I need a connected reasoning cortex for that request.", null);
        }
        try {
            return provider.proposeReasoning(request, tools);
        } catch (Exception failure) {
            return new ReasoningResult(provider.id(), "The optional reasoning cortex is unavailable right now.", null);
        }
    }
}
