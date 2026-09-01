package com.jarvis.mobile.ui;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;

import com.jarvis.mobile.CommandsActivity;
import com.jarvis.mobile.DeveloperSettingsActivity;
import com.jarvis.mobile.MainActivity;
import com.jarvis.mobile.NotesActivity;
import com.jarvis.mobile.SettingsActivity;

/**
 * Narrow presentation bridge between Claude's canonical HTML and existing Android surfaces.
 *
 * This class does not own assistant logic. It only forwards UI actions to production activities
 * and Android settings so the canonical interface cannot grow a duplicate backend.
 */
public final class ClaudeUiActionRouter {
    public static final String ACTION_LISTEN = "listen";
    public static final String ACTION_SETTINGS = "settings";
    public static final String ACTION_DEVELOPER_OPTIONS = "developer_options";
    public static final String ACTION_HELP = "help";
    public static final String ACTION_NOTES = "notes";
    public static final String ACTION_MEMORY = "memory";
    public static final String ACTION_ROUTINES = "routines";
    public static final String ACTION_SKILLS = "skills";
    public static final String ACTION_OVERLAYS = "overlays";
    public static final String ACTION_ACTIVITY_FEED = "activity_feed";
    public static final String ACTION_CALENDAR = "calendar";
    public static final String ACTION_BROWSER = "browser";
    public static final String ACTION_HUB = "hub";
    public static final String ACTION_TASKS_PROJECTS = "tasks_projects";
    public static final String ACTION_MEDIA_PREVIOUS = "media_previous";
    public static final String ACTION_MEDIA_PLAY_PAUSE = "media_play_pause";
    public static final String ACTION_MEDIA_NEXT = "media_next";
    public static final String ACTION_DEFAULT_ASSISTANT = "default_assistant";
    public static final String ACTION_NOTIFICATION_ACCESS = "notification_access";
    public static final String ACTION_ACCESSIBILITY = "accessibility";

    private static final String SUPPORTED_ACTIONS_JSON = "[\"listen\",\"settings\",\"developer_options\",\"help\",\"notes\",\"memory\",\"routines\",\"skills\",\"overlays\",\"activity_feed\",\"calendar\",\"browser\",\"hub\",\"tasks_projects\",\"media_previous\",\"media_play_pause\",\"media_next\",\"default_assistant\",\"notification_access\",\"accessibility\"]";
    private static final int ASSISTANT_ROLE_REQUEST = 8101;

    private final Activity activity;

    public ClaudeUiActionRouter(Activity activity) {
        this.activity = activity;
    }

    /** Backward-compatible fire-and-forget entry point for the trusted packaged UI. */
    @JavascriptInterface
    public void action(String action) {
        activity.runOnUiThread(() -> dispatch(action));
    }

    /**
     * Structured entry point for the canonical UI.
     *
     * This reports whether Android accepted the presentation request for dispatch; it deliberately
     * does not pretend that a downstream activity or system panel completed successfully.
     */
    @JavascriptInterface
    public String actionWithResult(String action) {
        String safeAction = jsonEscape(action == null ? "" : action);
        if (!isSupported(action)) {
            return "{\"accepted\":false,\"action\":\"" + safeAction + "\",\"reason\":\"unsupported\"}";
        }
        activity.runOnUiThread(() -> dispatch(action));
        return "{\"accepted\":true,\"action\":\"" + safeAction + "\",\"reason\":\"queued\"}";
    }

    /** Allows the canonical HTML to hide/disable controls that are not wired in this APK. */
    @JavascriptInterface
    public boolean isSupported(String action) {
        if (action == null) return false;
        switch (action) {
            case ACTION_LISTEN:
            case ACTION_SETTINGS:
            case ACTION_DEVELOPER_OPTIONS:
            case ACTION_HELP:
            case ACTION_NOTES:
            case ACTION_MEMORY:
            case ACTION_ROUTINES:
            case ACTION_SKILLS:
            case ACTION_OVERLAYS:
            case ACTION_ACTIVITY_FEED:
            case ACTION_CALENDAR:
            case ACTION_BROWSER:
            case ACTION_HUB:
            case ACTION_TASKS_PROJECTS:
            case ACTION_MEDIA_PREVIOUS:
            case ACTION_MEDIA_PLAY_PAUSE:
            case ACTION_MEDIA_NEXT:
            case ACTION_DEFAULT_ASSISTANT:
            case ACTION_NOTIFICATION_ACCESS:
            case ACTION_ACCESSIBILITY:
                return true;
            default:
                return false;
        }
    }

    /** Stable JSON capability list for Claude's packaged UI; contains presentation actions only. */
    @JavascriptInterface
    public String supportedActions() {
        return SUPPORTED_ACTIONS_JSON;
    }

    void dispatch(String action) {
        if (!isSupported(action)) return;
        switch (action) {
            case ACTION_LISTEN:
                Intent assist = new Intent(activity, MainActivity.class);
                assist.setAction(Intent.ACTION_ASSIST);
                activity.startActivity(assist);
                break;
            case ACTION_SETTINGS:
                activity.startActivity(new Intent(activity, SettingsActivity.class));
                break;
            case ACTION_DEVELOPER_OPTIONS:
                activity.startActivity(new Intent(activity, DeveloperSettingsActivity.class));
                break;
            case ACTION_HELP:
                activity.startActivity(new Intent(activity, CommandsActivity.class));
                break;
            case ACTION_NOTES:
                activity.startActivity(new Intent(activity, NotesActivity.class));
                break;
            case ACTION_MEMORY:
                activity.startActivity(new Intent(activity, MemoryActivity.class));
                break;
            case ACTION_ROUTINES:
                activity.startActivity(new Intent(activity, RoutinesActivity.class));
                break;
            case ACTION_SKILLS:
                activity.startActivity(new Intent(activity, SkillsActivity.class));
                break;
            case ACTION_OVERLAYS:
                activity.startActivity(new Intent(activity, OverlaysActivity.class));
                break;
            case ACTION_ACTIVITY_FEED:
                activity.startActivity(new Intent(activity, ActivityFeedActivity.class));
                break;
            case ACTION_CALENDAR:
                activity.startActivity(new Intent(activity, CalendarActivity.class));
                break;
            case ACTION_BROWSER:
                activity.startActivity(new Intent(activity, BrowserActivity.class));
                break;
            case ACTION_HUB:
                activity.startActivity(new Intent(activity, JarvisHubActivity.class));
                break;
            case ACTION_TASKS_PROJECTS:
                activity.startActivity(new Intent(activity, TasksProjectsActivity.class));
                break;
            case ACTION_MEDIA_PREVIOUS:
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                break;
            case ACTION_MEDIA_PLAY_PAUSE:
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                break;
            case ACTION_MEDIA_NEXT:
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
                break;
            case ACTION_DEFAULT_ASSISTANT:
                requestAssistantRole();
                break;
            case ACTION_NOTIFICATION_ACCESS:
                activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                break;
            case ACTION_ACCESSIBILITY:
                activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                break;
            default:
                break;
        }
    }

    private void dispatchMediaKey(int keyCode) {
        AudioManager audio = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private void requestAssistantRole() {
        RoleManager roles = activity.getSystemService(RoleManager.class);
        if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT) ||
                roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            return;
        }
        activity.startActivityForResult(
                roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),
                ASSISTANT_ROLE_REQUEST);
    }

    private static String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
