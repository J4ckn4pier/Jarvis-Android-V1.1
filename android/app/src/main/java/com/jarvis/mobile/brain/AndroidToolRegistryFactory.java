package com.jarvis.mobile.brain;

import android.content.Context;

import com.jarvis.brain.ExternalResearchGateway;
import com.jarvis.brain.ToolExecutionClass;
import com.jarvis.brain.ToolRegistry;
import com.jarvis.brain.ToolResult;
import com.jarvis.brain.ToolSpec;
import com.jarvis.mobile.actions.AndroidActionRouter;
import com.jarvis.mobile.actions.AndroidDialerActions;
import com.jarvis.mobile.actions.AndroidFlashlightActions;
import com.jarvis.mobile.actions.AndroidMediaActions;
import com.jarvis.mobile.actions.AndroidMessagingActions;
import com.jarvis.mobile.actions.AndroidNavigationActions;
import com.jarvis.mobile.actions.AndroidTimerActions;
import com.jarvis.mobile.calendar.AndroidCalendarReader;
import com.jarvis.mobile.memory.JarvisDatabase;

import java.util.Map;
import java.util.Set;

/** Android hands for the shared brain tool registry. Research remains a separate injected gateway. */
public final class AndroidToolRegistryFactory {
    private AndroidToolRegistryFactory() {}

    public static ToolRegistry create(Context context, ExternalResearchGateway research) {
        Context appContext = context.getApplicationContext();
        AndroidActionRouter actions = new AndroidActionRouter(appContext);
        AndroidDialerActions dialer = new AndroidDialerActions(appContext);
        AndroidFlashlightActions flashlight = new AndroidFlashlightActions(appContext);
        AndroidMediaActions media = new AndroidMediaActions(appContext);
        AndroidMessagingActions messaging = new AndroidMessagingActions(appContext);
        AndroidNavigationActions navigation = new AndroidNavigationActions(appContext);
        AndroidTimerActions timer = new AndroidTimerActions(appContext);
        AndroidCalendarReader calendar = new AndroidCalendarReader(appContext);
        ToolRegistry registry = ToolRegistry.standard(research);

        register(registry, "open_dialer", false, Set.of("phone", "phone app", "dialer"), Set.of(),
                "Open Android phone dialer", ToolExecutionClass.DEVICE_REFLEX,
                args -> dialer.openDialer());
        register(registry, "set_timer", false, Set.of("timer"), Set.of("amount", "unit"),
                "Set Android timer", ToolExecutionClass.DEVICE_REFLEX,
                args -> timer.setTimer(args.get("amount"), args.get("unit")));
        register(registry, "navigate", false, Set.of("directions", "navigation"), Set.of("destination"),
                "Open navigation", ToolExecutionClass.DEVICE_REFLEX,
                args -> navigation.navigate(args.get("destination")));
        register(registry, "media_play", false, Set.of("play music", "play media"), Set.of("query"),
                "Play requested media", ToolExecutionClass.DEVICE_REFLEX,
                args -> media.playMediaQuery(args.get("query")));
        register(registry, "set_flashlight", false, Set.of("flashlight", "torch"), Set.of("state"),
                "Set flashlight state", ToolExecutionClass.DEVICE_REFLEX,
                args -> flashlight.setState(args.get("state")));
        register(registry, "calendar_query", false, Set.of("calendar", "schedule"), Set.of("when"),
                "Read calendar commitments", ToolExecutionClass.DEVICE_REFLEX,
                args -> calendar.commitments(args.get("when")));
        register(registry, "create_reminder", false, Set.of("reminder", "remind me"), Set.of("request"),
                "Open the Android calendar editor with the requested reminder details for user confirmation",
                ToolExecutionClass.DEVICE_REFLEX,
                args -> actions.execute("schedule " + args.get("request")));
        register(registry, "notification_query", false, Set.of("notifications", "notification"), Set.of(),
                "Read captured notifications", ToolExecutionClass.DEVICE_REFLEX,
                args -> {
                    String notifications = JarvisDatabase.get(appContext).recentNotifications(10);
                    return notifications.isBlank() ? "No captured notifications." : notifications;
                });
        register(registry, "send_message", true, Set.of("text", "message"), Set.of("recipient", "message"),
                "Prepare external message after approval", ToolExecutionClass.CONSEQUENTIAL,
                args -> messaging.prepareMessage(args.get("recipient"), args.get("message")));
        return registry;
    }

    private interface Command { String run(Map<String,String> args); }

    private static void register(ToolRegistry registry, String name, boolean consequential, Set<String> aliases,
                                 Set<String> required, String description, ToolExecutionClass executionClass, Command command) {
        registry.register(new ToolSpec(name, consequential, aliases, required, description, executionClass), (args, ctx) -> {
            String result = command.run(args);
            if (result == null || result.isBlank()) return ToolResult.failure("Android capability did not return an outcome");
            String lower = result.toLowerCase(java.util.Locale.ROOT);
            if (isFailureOutcome(lower)) return ToolResult.failure(result);
            return ToolResult.success(result);
        });
    }

    private static boolean isFailureOutcome(String lower) {
        return lower.contains("couldn’t")
                || lower.contains("couldn't")
                || lower.contains("failed")
                || lower.contains("blocked")
                || lower.startsWith("no compatible")
                || lower.startsWith("tell me")
                || lower.startsWith("enable ")
                || lower.contains("unavailable")
                || lower.contains("must be")
                || lower.contains("too large")
                || lower.contains("does not expose");
    }
}
