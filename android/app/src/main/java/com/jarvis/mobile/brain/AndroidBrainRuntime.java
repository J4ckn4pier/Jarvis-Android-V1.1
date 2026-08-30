package com.jarvis.mobile.brain;

import android.content.Context;
import android.util.Log;
import com.jarvis.brain.*;
import com.jarvis.mobile.brain.providers.CortexProvider;
import com.jarvis.mobile.brain.providers.CortexProviderFactory;
import com.jarvis.mobile.remote.RemoteGoalClient;
import com.jarvis.mobile.remote.RemoteGoalStateStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Android composition root for the shared brain. UI/voice surfaces must use this instead of owning intent logic. */
public final class AndroidBrainRuntime {
    private static final String TRACE_TAG = "JARVIS_COMMAND_TRACE";
    private final BrainRuntime runtime;
    private final RuntimeApprovalConversation conversation;
    private final SettingsStore settings;
    private final OutcomeFollowupRuntime followups;
    private final JarvisUiBackend uiBackend;
    private final MemoryConsolidator memoryConsolidator;
    private final Clock clock;

    public AndroidBrainRuntime(Context context) {
        Context app = context.getApplicationContext();
        clock = Clock.systemDefaultZone();
        settings = new SettingsStore(new AndroidSharedPreferencesSettingsPersistence(app));
        ExternalResearchGateway research = AndroidExternalResearchGateway.create(app, settings);
        ToolRegistry tools = AndroidToolRegistryFactory.create(app, research);
        ConnectionRegistry connections = new ConnectionRegistry(new AndroidConnectionRegistryPersistence(app));

        ReasoningRouter localReasoning = request -> reasonWithConfiguredCortex(app, request, tools);
        ReasoningRouter reasoning = remoteReasoningOrLocal(app, localReasoning);

        LongTermMemoryStore memory = new LongTermMemoryStore(new AndroidLongTermMemoryPersistence(
                app.getNoBackupFilesDir().toPath().resolve("jarvis").resolve("long-term-memory.bin"),
                new AndroidKeystoreMemoryCipher("jarvis.long.term.memory.v1")));
        memoryConsolidator = new MemoryConsolidator(new RuleMemoryExtractor(), memory);
        MemoryContextSource memoryContext = new MemoryContextSource(memory, clock, 8);
        DeviceStateStore devices = new DeviceStateStore(new AndroidDeviceStateStorePersistence(app));
        AssistantContextSource notificationContext = new KeywordGatedAssistantContextSource(
                new AndroidRecentNotificationContextSource(app),
                Set.of("notification", "notifications", "what did i miss"));
        AssistantContextSource runtimeContext = new CompositeAssistantContextSource(List.of(
                memoryContext,
                new RuntimeEnvironmentContextSource(clock, devices),
                notificationContext));

        OutcomeFollowupStore followupStore = new EncryptedFileOutcomeFollowupStore(
                app.getNoBackupFilesDir().toPath().resolve("jarvis").resolve("pending-outcome-followups.bin"),
                new AndroidKeystoreMemoryCipher());
        OutcomeFollowupCoordinator followupCoordinator = new OutcomeFollowupCoordinator(
                new EpisodeFollowupPolicy(Duration.ofMinutes(30)),
                new ProactiveExecutive(new AttentionGate(0.70), Duration.ZERO, true),
                followupStore);
        followups = new OutcomeFollowupRuntime(settings, followupCoordinator);

        BrainEngine brain = BrainEngine.createDefault(clock);
        brain.beginInvokedConversation();
        AssistantCore assistant = new AssistantCore(brain, reasoning, tools, runtimeContext);
        runtime = new BrainRuntime(assistant, tools, clock, followups::recordActedOn);
        conversation = new RuntimeApprovalConversation(runtime);

        DefaultAppPreferenceStore defaultApps = new DefaultAppPreferenceStore(new AndroidDefaultAppPreferencePersistence(app));
        UiListStore lists = new UiListStore(new AndroidUiListStorePersistence(app));
        RoutineStore routines = new RoutineStore(new AndroidRoutineStorePersistence(app));
        ActivityLog activity = new ActivityLog(new AndroidActivityLogPersistence(app));
        MusicQueueStore music = new MusicQueueStore(new AndroidMusicQueueStorePersistence(app));
        uiBackend = new JarvisUiBackend(memory, tools, connections, settings, defaultApps, lists, routines, activity, devices, music);
    }

    public BrainRuntime.Result handle(String utterance) { return runtime.handle(utterance); }
    public BrainRuntime.Result approvePending() { return runtime.approvePending(); }
    public BrainRuntime.Result retryPending() { return runtime.retryPending(); }
    public void cancelPending() { runtime.cancelPending(); }
    public boolean hasPendingApproval() { return runtime.hasPendingApproval(); }
    public boolean hasPendingRecovery() { return runtime.hasPendingRecovery(); }

    public RuntimeSurfacePresentation handlePresentation(String utterance) { return handlePresentation(utterance, 1.0); }

    public RuntimeSurfacePresentation handlePresentation(String utterance, double speechConfidence) {
        Log.i(TRACE_TAG, "JARVIS_RUNTIME_INPUT utterance=" + String.valueOf(utterance));
        memoryConsolidator.ingestUserTurn(utterance, speechConfidence, clock.instant());
        RuntimeSurfacePresentation presentation = conversation.handle(utterance, speechConfidence);
        Log.i(TRACE_TAG, "JARVIS_RUNTIME_OUTPUT state=" + presentation.state() + " text=" + presentation.text());
        return presentation;
    }

    public RuntimeSurfacePresentation approvePresentation() { return conversation.approvePending(); }
    public RuntimeSurfacePresentation retryPresentation() { return conversation.retryPending(); }
    public RuntimeSurfacePresentation cancelPresentation() { return conversation.cancelPending(); }

    public void recordActedOnEpisode(RecommendationEpisode episode, Instant actedAt) { followups.recordActedOn(episode, actedAt); }
    public Optional<ProactiveIntervention> onOutcomeFollowupSignal(OutcomeFollowupSignal signal) { return followups.onSignal(signal); }
    public SettingsStore settings() { return settings; }
    public JarvisUiBackend uiBackend() { return uiBackend; }

    private static ReasoningRouter remoteReasoningOrLocal(Context app, ReasoningRouter localReasoning) {
        RemoteGoalStateStore state = new RemoteGoalStateStore(app);
        RemoteGoalStateStore.Connection connection = state.loadConnection();
        if (connection == null) return localReasoning;
        try {
            RemoteGoalClient client = new RemoteGoalClient(connection.baseUrl(), connection.token());
            return request -> {
                try {
                    RemoteGoalClient.GoalSubmission submitted = client.submitGoal(
                            request.utterance(), "primary", List.of(), List.of(), null);
                    state.saveProject(submitted.projectId());
                    return new ReasoningResult(
                            "remote-goal",
                            "Certainly, sir. I've started that and I'll keep you updated.",
                            null);
                } catch (RemoteGoalClient.RemoteGoalException unavailable) {
                    return localReasoning.reason(request);
                }
            };
        } catch (IllegalArgumentException invalidConnection) {
            return localReasoning;
        }
    }

    private static ReasoningResult reasonWithConfiguredCortex(Context context, ReasoningRequest request, ToolRegistry tools) {
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
