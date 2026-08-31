package com.jarvis.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Lightweight launcher gate that makes the Android system requirement for passive wake explicit.
 * It never blocks use of the app: the user may defer the role and continue into JARVIS.
 */
public final class AssistantRoleOnboardingActivity extends Activity {
    private static final int ASSISTANT_ROLE_REQUEST = 91;
    private static final String PREFS = "jarvis_shell";
    private static final String KEY_PROMPTED = "assistant_role_prompted";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        RoleManager role = getSystemService(RoleManager.class);
        if (role == null || !role.isRoleAvailable(RoleManager.ROLE_ASSISTANT) || role.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            openJarvis();
            return;
        }

        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean wakeEnabled = preferences.getBoolean("wake_enabled", true);
        boolean alreadyPrompted = preferences.getBoolean(KEY_PROMPTED, false);
        if (!wakeEnabled || alreadyPrompted) {
            openJarvis();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Enable JARVIS wake word")
                .setMessage("Wake word requires JARVIS as your Android assistant. Android keeps the selected Voice Interaction Service available so JARVIS can listen locally for ‘Jarvis’ or ‘Hey Jarvis’. You can change this later in Settings.")
                .setPositiveButton("MAKE JARVIS ASSISTANT", (dialog, which) -> requestAssistantRole(role))
                .setNegativeButton("NOT NOW", (dialog, which) -> {
                    preferences.edit().putBoolean(KEY_PROMPTED, true).apply();
                    openJarvis();
                })
                .setOnCancelListener(dialog -> {
                    preferences.edit().putBoolean(KEY_PROMPTED, true).apply();
                    openJarvis();
                })
                .show();
    }

    private void requestAssistantRole(RoleManager role) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_PROMPTED, true).apply();
        try {
            startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), ASSISTANT_ROLE_REQUEST);
        } catch (RuntimeException failure) {
            Toast.makeText(this, "Android could not open Assistant selection. You can enable it from JARVIS Settings.", Toast.LENGTH_LONG).show();
            openJarvis();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ASSISTANT_ROLE_REQUEST) openJarvis();
    }

    private void openJarvis() {
        Intent launch = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(launch);
        finish();
    }
}
