package com.jarvis.mobile.brain;

import android.content.Context;
import android.util.Log;
import com.jarvis.brain.*;
import com.jarvis.mobile.brain.providers.CortexProvider;
import com.jarvis.mobile.brain.providers.CortexProviderFactory;
import com.jarvis.mobile.remote.RemoteGoalClient;
import com.jarvis.mobile.remote.RemoteGoalCoordinator;
import com.jarvis.mobile.remote.RemoteGoalStateStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
    private final Context app;
    private volatile boolean remoteApprovalVisible;
    private volatile boolean remoteProjectVisible;

    public AndroidBrainRuntime(Context context) {
        app = context.getApplicationContext();
        clock = Clock.systemDefaultZone();
        settings = new SettingsStore(new AndroidSharedPreferencesSettingsPersistence(app));
        ExternalResearchGateway research = AndroidExternalResearchGateway.create(app, settings);
        ToolRegistry tools = AndroidToolRegistryFactory.create(app, research);
        ConnectionRegistry connections = new ConnectionRegistry(new AndroidConnectionRegistryPersistence(app));

        ReasoningRouter localReasoning = request -> reasonWithConfiguredCortex(app, request, tools);
        ReasoningRouter reasoning = selectiveReasoning(app, localReasoning);

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
        remoteApprovalVisible = false;
        remoteProjectVisible = false;
        Log.i(TRACE_TAG, "JARVIS_RUNTIME_INPUT utterance=" + String.valueOf(utterance));
        memoryConsolidator.ingestUserTurn(utterance, speechConfidence, clock.instant());
        RuntimeSurfacePresentation presentation = conversation.handle(utterance, speechConfidence);
        Log.i(TRACE_TAG, "JARVIS_RUNTIME_OUTPUT state=" + presentation.state() + " text=" + presentation.text());
        return presentation;
    }

    /** Returns a normal JARVIS presentation only when a saved long-running goal has new visible state. */
    public Optional<RuntimeSurfacePresentation> resumeRemoteGoalPresentation() {
        RemoteGoalStateStore state = new RemoteGoalStateStore(app);
        RemoteGoalStateStore.State saved = state.load();
        if (!saved.hasProject()) {
            remoteApprovalVisible = false;
            remoteProjectVisible = false;
            return Optional.empty();
        }
        RemoteGoalStateStore.Connection connection = state.loadConnection();
        if (connection == null) return Optional.empty();
        try {
            RemoteGoalClient client = new RemoteGoalClient(connection.baseUrl(), connection.token());
            RemoteGoalCoordinator coordinator = new RemoteGoalCoordinator(client, state);
            Optional<RemoteGoalCoordinator.Snapshot> resumed = coordinator.resumeActiveProject();
            if (resumed.isEmpty()) return Optional.empty();
            RemoteGoalCoordinator.Snapshot snapshot = resumed.get();
            RemoteGoalClient.ProjectStatus project = snapshot.project();

            if (snapshot.completed()) {
                remoteApprovalVisible = false;
                remoteProjectVisible = false;
                state.clearProject();
                return Optional.of(new RuntimeSurfacePresentation(
                        AssistantSurfaceState.ACTION_DONE,
                        snapshot.result().result(),
                        "Finished in the background.",
                        RuntimeSurfaceAction.NONE,
                        RuntimeSurfaceAction.NONE));
            }
            if ("failed".equalsIgnoreCase(project.state()) || "cancelled".equalsIgnoreCase(project.state())) {
                remoteApprovalVisible = false;
                remoteProjectVisible = false;
                state.clearProject();
                return Optional.of(new RuntimeSurfacePresentation(
                        AssistantSurfaceState.ERROR,
                        "That background task stopped before it completed.",
                        "",
                        RuntimeSurfaceAction.NONE,
                        RuntimeSurfaceAction.NONE));
            }

            remoteProjectVisible = true;
            if (project.pendingApprovals().size() == 1) {
                remoteApprovalVisible = true;
                return Optional.of(new RuntimeSurfacePresentation(
                        AssistantSurfaceState.AWAITING_APPROVAL,
                        "That background task needs your approval, sir.",
                        "Review the requested step before choosing APPROVE or CANCEL.",
                        RuntimeSurfaceAction.APPROVE,
                        RuntimeSurfaceAction.CANCEL));
            }
            if (project.pendingApprovals().size() > 1) {
                remoteApprovalVisible = false;
                return Optional.of(new RuntimeSurfacePresentation(
                        AssistantSurfaceState.ERROR,
                        "That background task has more than one approval waiting, so I won't guess which one you mean.",
                        "Open it again after the approvals have been narrowed to one.",
                        RuntimeSurfaceAction.NONE,
                        RuntimeSurfaceAction.CANCEL));
            }

            remoteApprovalVisible = false;
            if (snapshot.events().events().isEmpty()) return Optional.empty();

            int completed = project.taskStates().getOrDefault("completed", 0);
            String detail = project.taskCount() > 0
                    ? "Progress: " + completed + " of " + project.taskCount() + " steps complete."
                    : "I have new progress on that task.";
            return Optional.of(new RuntimeSurfacePresentation(
                    AssistantSurfaceState.RESPONDING,
                    "I'm still working on that, sir.",
                    detail,
                    RuntimeSurfaceAction.NONE,
                    RuntimeSurfaceAction.CANCEL));
        } catch (RemoteGoalClient.RemoteGoalException | IllegalArgumentException unavailable) {
            return Optional.empty();
        }
    }

    public RuntimeSurfacePresentation approveRemoteGoalPresentation() {
        return respondToRemoteApproval(true);
    }

    public RuntimeSurfacePresentation declineRemoteGoalPresentation() {
        return respondToRemoteApproval(false);
    }

    public RuntimeSurfacePresentation cancelRemoteGoalPresentation() {
        RemoteGoalCoordinator coordinator = remoteCoordinator();
        if (coordinator == null) return remoteUnavailablePresentation();
        try {
            if (!coordinator.cancelActiveProject()) {
                return new RuntimeSurfacePresentation(
                        AssistantSurfaceState.ERROR,
                        "I couldn't confirm that the background task was cancelled.",
                        "I kept its local project state so I can check it again.",
                        RuntimeSurfaceAction.NONE,
                        RuntimeSurfaceAction.CANCEL);
            }
            remoteApprovalVisible = false;
            remoteProjectVisible = false;
            return new RuntimeSurfacePresentation(
                    AssistantSurfaceState.ACTION_DONE,
                    "Cancelled, sir.",
                    "The remote task confirmed cancellation.",
                    RuntimeSurfaceAction.NONE,
                    RuntimeSurfaceAction.NONE);
        } catch (RemoteGoalClient.RemoteGoalException unavailable) {
            return remoteUnavailablePresentation();
        }
    }

    private RuntimeSurfacePresentation respondToRemoteApproval(boolean approved) {
        RemoteGoalCoordinator coordinator = remoteCoordinator();
        if (coordinator == null) return remoteUnavailablePresentation();
        try {
            Optional<RemoteGoalClient.ApprovalDecision> decision = coordinator.respondToActiveApproval(approved, null);
            if (decision.isEmpty()) {
                return new RuntimeSurfacePresentation(
                        AssistantSurfaceState.ERROR,
                        "I couldn't identify exactly one approval to act on, so I didn't change anything.",
                        "",
                        RuntimeSurfaceAction.NONE,
                        RuntimeSurfaceAction.CANCEL);
            }
            remoteApprovalVisible = false;
            remoteProjectVisible = true;
            return new RuntimeSurfacePresentation(
                    AssistantSurfaceState.RESPONDING,
                    approved ? "Approved. I'll continue that background task, sir." : "Understood. I declined that step.",
                    "",
                    RuntimeSurfaceAction.NONE,
                    RuntimeSurfaceAction.CANCEL);
        } catch (RemoteGoalClient.RemoteGoalException unavailable) {
            return remoteUnavailablePresentation();
        }
    }

    private RemoteGoalCoordinator remoteCoordinator() {
        RemoteGoalStateStore state = new RemoteGoalStateStore(app);
        RemoteGoalStateStore.State saved = state.load();
        RemoteGoalStateStore.Connection connection = state.loadConnection();
        if (!saved.hasProject() || connection == null) return null;
        try {
            return new RemoteGoalCoordinator(new RemoteGoalClient(connection.baseUrl(), connection.token()), state);
        } catch (IllegalArgumentException invalidConnection) {
            return null;
        }
    }

    private RuntimeSurfacePresentation remoteUnavailablePresentation() {
        return new RuntimeSurfacePresentation(
                AssistantSurfaceState.ERROR,
                "I couldn't reach that background task right now.",
                "I kept its project state so I can try again when the connection is available.",
                RuntimeSurfaceAction.NONE,
                RuntimeSurfaceAction.CANCEL);
    }

    public RuntimeSurfacePresentation approvePresentation() {
        return remoteApprovalVisible ? approveRemoteGoalPresentation() : conversation.approvePending();
    }
    public RuntimeSurfacePresentation retryPresentation() { return conversation.retryPending(); }
    public RuntimeSurfacePresentation cancelPresentation() {
        if (remoteApprovalVisible) return declineRemoteGoalPresentation();
        if (remoteProjectVisible) return cancelRemoteGoalPresentation();
        return conversation.cancelPending();
    }

    public void recordActedOnEpisode(RecommendationEpisode episode, Instant actedAt) { followups.recordActedOn(episode, actedAt); }
    public Optional<ProactiveIntervention> onOutcomeFollowupSignal(OutcomeFollowupSignal signal) { return followups.onSignal(signal); }
    public SettingsStore settings() { return settings; }
    public JarvisUiBackend uiBackend() { return uiBackend; }

    /**
     * Keeps normal assistant conversation and one-device reasoning local, but hands genuinely complex,
     * multi-part projects to the provider-neutral background orchestrator when one is configured.
     */
    private static ReasoningRouter selectiveReasoning(Context app, ReasoningRouter localReasoning) {
        ReasoningRouter remoteReasoning = remoteReasoningOrLocal(app, localReasoning);
        return request -> shouldDelegateComplexGoal(request.utterance())
                ? remoteReasoning.reason(request)
                : localReasoning.reason(request);
    }

    private static boolean shouldDelegateComplexGoal(String utterance) {
        String lower = utterance == null ? "" : utterance.toLowerCase(Locale.ROOT);
        boolean projectSignal = lower.contains("multi-step")
                || lower.contains("multistep")
                || lower.contains("project")
                || lower.contains("workflow")
                || lower.contains("long-running")
                || lower.contains("long running");
        boolean synthesisSignal = lower.contains("research")
                || lower.contains("compare")
                || lower.contains("approach")
                || lower.contains("recommendation")
                || lower.contains("tradeoff")
                || lower.contains("trade-off")
                || lower.contains("analyze");
        boolean coordinationSignal = lower.contains("multiple")
                || lower.contains("several")
                || lower.contains("steps")
                || lower.contains("tasks")
                || lower.contains("workers")
                || lower.contains("coordinate")
                || lower.contains("in the background");
        return (projectSignal && synthesisSignal)
                || (projectSignal && coordinationSignal)
                || (synthesisSignal && coordinationSignal);
    }

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
