package com.jarvis.mobile.ui;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.provider.Settings;
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
    public static final String ACTION_DEFAULT_ASSISTANT = "default_assistant";
    public static final String ACTION_NOTIFICATION_ACCESS = "notification_access";
    public static final String ACTION_ACCESSIBILITY = "accessibility";

    private static final int ASSISTANT_ROLE_REQUEST = 8101;

    private final Activity activity;

    public ClaudeUiActionRouter(Activity activity) {
        this.activity = activity;
    }

    /** Entry point exposed to the trusted, packaged canonical UI only. */
    @JavascriptInterface
    public void action(String action) {
        activity.runOnUiThread(() -> dispatch(action));
    }

    void dispatch(String action) {
        if (action == null) return;
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
                // Unknown actions are intentionally ignored rather than interpreted dynamically.
                break;
        }
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
}
