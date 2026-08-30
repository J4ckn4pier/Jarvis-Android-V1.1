package com.jarvis.mobile.remote;

import java.util.Optional;

/** Reconnect/progress/result/cancel/approval coordinator for one durable provider-neutral remote project. */
public final class RemoteGoalCoordinator {
    private final RemoteGoalClient client;
    private final RemoteGoalStateStore state;

    public RemoteGoalCoordinator(RemoteGoalClient client, RemoteGoalStateStore state) {
        this.client = client;
        this.state = state;
    }

    public Optional<Snapshot> resumeActiveProject() throws RemoteGoalClient.RemoteGoalException {
        RemoteGoalStateStore.State saved = state.load();
        if (!saved.hasProject()) return Optional.empty();
        String projectId = saved.projectId();
        final RemoteGoalClient.ProjectStatus project;
        try {
            project = client.getProject(projectId);
        } catch (RemoteGoalClient.RemoteGoalException missing) {
            if (missing.statusCode() != 404) throw missing;
            state.clearProject();
            return Optional.empty();
        }
        RemoteGoalClient.EventPage page;
        try {
            page = client.getEvents(projectId, saved.eventId());
        } catch (RemoteGoalClient.RemoteGoalException expired) {
            if (expired.statusCode() != 410 || saved.eventId() == null) throw expired;
            state.saveCursor(projectId, null);
            page = client.getEvents(projectId, null);
        }
        if (page.nextEventId() != null) state.saveCursor(projectId, page.nextEventId());
        RemoteGoalClient.GoalResult result = null;
        if ("completed".equalsIgnoreCase(project.state())) result = client.getResult(projectId);
        return Optional.of(new Snapshot(project, page, result));
    }

    public Optional<RemoteGoalClient.ApprovalDecision> respondToActiveApproval(
            boolean approved, String response) throws RemoteGoalClient.RemoteGoalException {
        RemoteGoalStateStore.State saved = state.load();
        if (!saved.hasProject()) return Optional.empty();
        String projectId = saved.projectId();
        RemoteGoalClient.ProjectStatus project = client.getProject(projectId);
        if (project.pendingApprovals().size() != 1) return Optional.empty();
        RemoteGoalClient.PendingApproval pending = project.pendingApprovals().get(0);
        return Optional.of(client.respondToApproval(projectId, pending.approvalId(), approved, response));
    }

    public boolean cancelActiveProject() throws RemoteGoalClient.RemoteGoalException {
        RemoteGoalStateStore.State saved = state.load();
        if (!saved.hasProject()) return false;
        String projectId = saved.projectId();
        RemoteGoalClient.Cancellation cancelled = client.cancel(projectId);
        if (!"cancelled".equalsIgnoreCase(cancelled.state())) return false;
        state.clearProject();
        return true;
    }

    public record Snapshot(RemoteGoalClient.ProjectStatus project,
                           RemoteGoalClient.EventPage events,
                           RemoteGoalClient.GoalResult result) {
        public boolean completed() { return result != null; }
    }
}
