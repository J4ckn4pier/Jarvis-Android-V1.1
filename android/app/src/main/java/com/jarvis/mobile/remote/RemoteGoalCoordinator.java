package com.jarvis.mobile.remote;

import java.util.Optional;

/** Reconnect/progress/result/cancel coordinator for one durable provider-neutral remote project. */
public final class RemoteGoalCoordinator {
    private final RemoteGoalClient client;
    private final RemoteGoalStateStore state;

    public RemoteGoalCoordinator(RemoteGoalClient client, RemoteGoalStateStore state) {
        this.client = client;
        this.state = state;
    }

    public Optional<Snapshot> resumeActiveProject() throws RemoteGoalClient.RemoteGoalException {
        RemoteGoalStateStore.State saved = state.load();
        if (saved == null) return Optional.empty();
        String projectId = saved.projectId();
        RemoteGoalClient.ProjectStatus project = client.getProject(projectId);
        RemoteGoalClient.EventPage page = client.getEvents(projectId, saved.eventId());
        if (page.nextEventId() != null) state.saveCursor(projectId, page.nextEventId());
        RemoteGoalClient.GoalResult result = null;
        if ("completed".equalsIgnoreCase(project.state())) result = client.getResult(projectId);
        return Optional.of(new Snapshot(project, page, result));
    }

    public boolean cancelActiveProject() throws RemoteGoalClient.RemoteGoalException {
        RemoteGoalStateStore.State saved = state.load();
        if (saved == null) return false;
        String projectId = saved.projectId();
        client.cancel(projectId);
        state.clearProject();
        return true;
    }

    public record Snapshot(RemoteGoalClient.ProjectStatus project,
                           RemoteGoalClient.EventPage events,
                           RemoteGoalClient.GoalResult result) {
        public boolean completed() { return result != null; }
    }
}
