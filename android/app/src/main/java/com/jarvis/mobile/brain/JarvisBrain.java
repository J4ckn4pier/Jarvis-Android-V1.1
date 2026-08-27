package com.jarvis.mobile.brain;

import android.content.Context;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;

import com.jarvis.mobile.actions.AndroidActionRouter;
import com.jarvis.mobile.brain.core.BrainResult;
import com.jarvis.mobile.brain.core.IntentPlan;
import com.jarvis.mobile.brain.core.LocalIntentEngine;
import com.jarvis.mobile.brain.providers.CortexProvider;
import com.jarvis.mobile.brain.providers.CortexProviderFactory;
import com.jarvis.mobile.memory.JarvisDatabase;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android executive cortex: understand, plan, validate, dispatch and synthesize.
 * Providers may propose plans, but only this class can send canonical actions to Android hands.
 */
public final class JarvisBrain {
    public interface Callback { void onResult(BrainResult result); }

    private final Context context;
    private final JarvisDatabase memory;
    private final AndroidActionRouter actions;
    private final LocalIntentEngine localLanguage;
    private final Handler main;
    private final ExecutorService providers;

    public JarvisBrain(Context context) {
        this.context = context.getApplicationContext();
        memory = JarvisDatabase.get(this.context);
        actions = new AndroidActionRouter(context);
        localLanguage = new LocalIntentEngine();
        main = new Handler(Looper.getMainLooper());
        providers = Executors.newSingleThreadExecutor();
    }

    /** Local-only synchronous path used by deterministic tests and reflex commands. */
    public String handle(String raw) {
        return execute(localLanguage.plan(raw), "local").spokenText();
    }

    public void handle(String raw, Callback callback) {
        IntentPlan local = localLanguage.plan(raw);
        if (local.isResolved() && local.intent() != IntentPlan.Intent.KNOWLEDGE_QUERY) {
            callback.onResult(execute(local, "local"));
            return;
        }
        CortexProvider provider = CortexProviderFactory.create(context);
        if (!provider.isConfigured() || "local".equals(provider.id())) {
            callback.onResult(local.intent() == IntentPlan.Intent.KNOWLEDGE_QUERY
                    ? execute(local, "local-search") : noCortexFallback(local));
            return;
        }
        providers.execute(() -> {
            try {
                IntentPlan plan = provider.propose(raw);
                main.post(() -> callback.onResult(plan == null || !plan.isResolved()
                        ? noCortexFallback(local) : execute(plan, provider.id())));
            } catch (Exception failure) {
                main.post(() -> callback.onResult(new BrainResult(local,
                        "The optional reasoning service is unavailable, so I kept the request local, sir.", false)));
            }
        });
    }

    public void handleCandidates(List<String> candidates, Callback callback) {
        IntentPlan local = localLanguage.planCandidates(candidates);
        if (local.isResolved() && local.intent() != IntentPlan.Intent.KNOWLEDGE_QUERY) {
            callback.onResult(execute(local, "local-speech"));
            return;
        }
        String bestTranscript = candidates == null || candidates.isEmpty() ? "" : candidates.get(0);
        handle(bestTranscript, callback);
    }

    public String cortexStatus() { return CortexProviderFactory.status(context); }

    private BrainResult execute(IntentPlan plan, String source) {
        String answer;
        switch (plan.intent()) {
            case HELP:
                answer = help();
                break;
            case REMEMBER:
                answer = remember(plan.payload());
                break;
            case RECALL:
                answer = recall(plan.payload());
                break;
            case ADD_TASK:
                answer = addTask(plan.payload());
                break;
            case LIST_TASKS:
                answer = listTasks();
                break;
            case COMPLETE_TASK:
                answer = completeTask(plan.payload());
                break;
            case NOTIFICATIONS:
                answer = notifications();
                break;
            case TIME:
                answer = "It’s " + DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(Calendar.getInstance().getTime()) + ", sir.";
                break;
            case DATE:
                answer = "Today is " + DateFormat.getDateInstance(DateFormat.FULL)
                        .format(Calendar.getInstance().getTime()) + ".";
                break;
            case BATTERY:
                answer = batteryStatus();
                break;
            case KNOWLEDGE_QUERY:
                answer = actions.execute("search " + plan.payload());
                if (answer == null || answer.trim().isEmpty()) {
                    answer = "I couldn’t open a private search for that question, sir.";
                }
                break;
            case GREETING:
            case IDENTITY:
            case THANKS:
            case CONVERSATION:
                answer = plan.answer();
                break;
            case UNKNOWN:
                return noCortexFallback(plan);
            default:
                answer = actions.execute(plan.canonicalCommand());
                if (answer == null || answer.trim().isEmpty()) {
                    answer = "I understood the request, but that Android capability is not connected yet, sir.";
                }
                break;
        }
        memory.logEvent("brain", source, plan.intent().name(), answer);
        return new BrainResult(plan, answer, true);
    }

    private BrainResult noCortexFallback(IntentPlan plan) {
        String answer = "I don’t yet have a reliable interpretation for that, sir. " +
                "Say “show my commands,” rephrase it as a direct phone action, or configure an optional reasoning cortex in Settings.";
        memory.logEvent("brain", "unresolved", plan.payload(), answer);
        return new BrainResult(plan, answer, false);
    }

    private String remember(String statement) {
        if (statement.isEmpty()) return "Tell me what you want remembered.";
        String clean = statement;
        if (clean.toLowerCase(Locale.ROOT).startsWith("that ")) clean = clean.substring(5).trim();
        int split = indexOfIgnoreCase(clean, " is ");
        if (split < 0) split = clean.indexOf('=');
        String key;
        String value;
        if (split > 0) {
            key = clean.substring(0, split).trim();
            value = clean.substring(split + (clean.charAt(split) == '=' ? 1 : 4)).trim();
        } else {
            key = "note " + System.currentTimeMillis();
            value = clean;
        }
        if (value.isEmpty()) return "Tell me the value you want remembered.";
        memory.remember(key, value);
        return key.startsWith("note ") ? "I’ll remember that, sir." : "I’ll remember " + key + ".";
    }

    private String recall(String key) {
        if (key.isEmpty()) return allMemories();
        String result = memory.recall(key);
        return result.isEmpty() ? "I don’t have a memory matching " + key + "." : result;
    }

    private String allMemories() {
        String result = memory.recentMemories(10);
        return result.isEmpty() ? "I don’t have any saved memories yet." : result;
    }

    private String addTask(String title) {
        if (title.isEmpty()) return "Tell me what task to add.";
        long id = memory.addTask(title);
        return id < 0 ? "I couldn’t save that task." : "Task " + id + " saved: " + title + ".";
    }

    private String listTasks() {
        String result = memory.openTasks(20);
        return result.isEmpty() ? "You have no open tasks." : result;
    }

    private String completeTask(String query) {
        if (query.isEmpty()) return "Tell me the task number or name.";
        return memory.completeTask(query) ? "Task completed." :
                "I couldn’t find an open task matching that.";
    }

    private String notifications() {
        String result = memory.recentNotifications(8);
        return result.isEmpty()
                ? "I don’t have any captured notifications yet. Enable Notification Awareness first."
                : result;
    }

    private String batteryStatus() {
        BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int level = manager == null ? -1
                : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return level < 0 ? "Android did not report the battery level." :
                "Battery power is at " + level + " percent, sir.";
    }

    private int indexOfIgnoreCase(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private String help() {
        return "You can speak naturally. I can call contacts or dial numbers; prepare texts and email; " +
                "create calendar events and invitations; set alarms and timers; open apps; search, show weather " +
                "or news, and navigate; control the flashlight, volume, and media; read notifications; remember " +
                "facts; manage tasks; report the time, date, and battery; and use optional Accessibility Device " +
                "Control to read the screen, tap, type, scroll, go Back, or go Home.";
    }
}
