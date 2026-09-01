package com.jarvis.mobile.ui;

import android.content.Intent;
import android.widget.LinearLayout;

/**
 * Entry point for the supplied Claude prototype screens that are backed by production state today.
 * Unsupported or storage-only mock surfaces are intentionally not exposed until they have real handlers.
 */
public final class JarvisHubActivity extends JarvisChromeActivity {
    @Override protected String screenTitle() { return "JARVIS"; }

    @Override protected void buildBody(LinearLayout body) {
        body.addView(section("ASSISTANT"));
        body.addView(row("Memory", "What JARVIS actually remembers", open(MemoryActivity.class)));
        body.addView(row("Activity", "Recorded JARVIS activity and outcomes", open(ActivityFeedActivity.class)));

        body.addView(section("PLANNING"));
        body.addView(row("Tasks & Projects", "Saved tasks and project state", open(TasksProjectsActivity.class)));

        body.addView(section("TOOLS"));
        body.addView(row("Music", "Now playing and persisted queue state", open(MusicActivity.class)));
        body.addView(row("Browser", "Saved bookmarks", open(BrowserActivity.class)));
        body.addView(row("Skills", "Capabilities actually registered in this build", open(SkillsActivity.class)));
        body.addView(row("Overlays", "Live popup overlay controls and Android surface links", open(OverlaysActivity.class)));
    }

    private Runnable open(Class<?> activity) {
        return () -> startActivity(new Intent(this, activity));
    }
}
