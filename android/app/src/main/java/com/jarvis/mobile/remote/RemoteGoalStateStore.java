package com.jarvis.mobile.remote;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists only public remote-project continuity data needed after app close/lock/restart. */
public final class RemoteGoalStateStore {
    private static final String PREFS = "jarvis_remote_goal_state";
    private static final String PROJECT_ID = "project_id";
    private static final String EVENT_ID = "event_id";

    private final SharedPreferences preferences;

    public RemoteGoalStateStore(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public State load() {
        return new State(preferences.getString(PROJECT_ID, null), preferences.getString(EVENT_ID, null));
    }

    public void saveProject(String projectId) {
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("project_id is required");
        preferences.edit().putString(PROJECT_ID, projectId).remove(EVENT_ID).apply();
    }

    public void saveCursor(String projectId, String eventId) {
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("project_id is required");
        SharedPreferences.Editor editor = preferences.edit().putString(PROJECT_ID, projectId);
        if (eventId == null) editor.remove(EVENT_ID); else editor.putString(EVENT_ID, eventId);
        editor.apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    public record State(String projectId, String eventId) {
        public boolean hasProject() { return projectId != null && !projectId.isBlank(); }
    }
}
