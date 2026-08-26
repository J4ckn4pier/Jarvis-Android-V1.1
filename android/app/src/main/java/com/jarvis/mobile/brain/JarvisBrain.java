package com.jarvis.mobile.brain;

import android.content.Context;

import com.jarvis.mobile.actions.AndroidActionRouter;
import com.jarvis.mobile.memory.JarvisDatabase;

import java.util.Locale;

public final class JarvisBrain {
    private final JarvisDatabase memory;
    private final AndroidActionRouter actions;

    public JarvisBrain(Context context) {
        Context app = context.getApplicationContext();
        memory = JarvisDatabase.get(app);
        actions = new AndroidActionRouter(context);
    }

    public String handle(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "I’m listening.";
        String command = raw.trim();
        String lower = command.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jarvis, ")) {
            command = command.substring(8).trim();
            lower = command.toLowerCase(Locale.ROOT);
        } else if (lower.startsWith("jarvis ")) {
            command = command.substring(7).trim();
            lower = command.toLowerCase(Locale.ROOT);
        }

        try {
            if (lower.startsWith("remember ")) return remember(command.substring(9).trim());
            if (lower.startsWith("recall ")) return recall(command.substring(7).trim());
            if (lower.equals("recall") || lower.equals("what do you remember")) return allMemories();
            if (lower.startsWith("what do you remember about ")) {
                return recall(command.substring("what do you remember about ".length()).trim());
            }
            if (lower.startsWith("what did i tell you about ")) {
                return recall(command.substring("what did i tell you about ".length()).trim());
            }

            if (lower.startsWith("add task ")) return addTask(command.substring(9).trim());
            if (lower.startsWith("create task ")) return addTask(command.substring(12).trim());
            if (lower.startsWith("remind me to ")) return addTask(command.substring(13).trim());
            if (lower.equals("tasks") || lower.equals("list tasks") ||
                    lower.equals("what are my tasks") || lower.equals("what do i need to do")) {
                return listTasks();
            }
            if (lower.startsWith("complete task ")) return completeTask(command.substring(14).trim());
            if (lower.startsWith("finish task ")) return completeTask(command.substring(12).trim());

            if (lower.equals("notifications") || lower.equals("read notifications") ||
                    lower.equals("what are my notifications") || lower.equals("what did i miss")) {
                String notifications = memory.recentNotifications(8);
                return notifications.isEmpty()
                        ? "I don’t have any captured notifications yet. Enable Notification Awareness first."
                        : notifications;
            }

            if (lower.equals("help") || lower.equals("what can you do")) return help();

            String actionResult = actions.execute(command);
            if (actionResult != null) return actionResult;
        } catch (Exception error) {
            return "I couldn’t complete that: " +
                    (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }

        return "I don’t have a safe deterministic action for that yet. Try “help” for the current beta commands.";
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
        return "I’ll remember " + (key.startsWith("note ") ? "that." : key + ".");
    }

    private String recall(String key) {
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
        return memory.completeTask(query) ? "Task completed." : "I couldn’t find an open task matching that.";
    }

    private int indexOfIgnoreCase(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private String help() {
        return "I can call contacts or numbers; draft texts and email; create calendar events and invites; " +
                "set alarms and timers; open apps; search and navigate; control flashlight, volume, and media; " +
                "read captured notifications; remember facts; store tasks; and use optional Accessibility control " +
                "to read the screen, tap, type, scroll, go Back, or go Home.";
    }
}
