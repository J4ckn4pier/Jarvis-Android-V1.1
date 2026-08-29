package com.jarvis.mobile.brain;

import android.content.Context;

import com.jarvis.brain.AssistantContextSource;
import com.jarvis.mobile.memory.JarvisDatabase;

/** Reads the app-private captured notification store; callers must apply an explicit relevance gate. */
public final class AndroidRecentNotificationContextSource implements AssistantContextSource {
    private final Context context;

    public AndroidRecentNotificationContextSource(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        this.context = context.getApplicationContext();
    }

    @Override
    public String contextFor(String utterance) {
        String recent = JarvisDatabase.get(context).recentNotifications(5);
        if (recent == null || recent.isBlank()) return "";
        return "Recent captured notifications:\n" + recent.trim();
    }
}
