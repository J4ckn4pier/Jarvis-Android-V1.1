package com.jarvis.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import com.jarvis.mobile.assistant.JarvisVoiceInteractionService;

/**
 * Lightweight launcher gate that makes Android's requirements for passive wake explicit.
 * It never blocks use of the app: the user may defer setup and continue into JARVIS.
 */
public final class AssistantRoleOnboardingActivity extends Activity {
    private static final int ASSISTANT_ROLE_REQUEST = 91;
    private static final int MICROPHONE_REQUEST = 92;
    private static final String PREFS = "jarvis_shell";
    private static final String KEY_PROMPTED = "assistant_role_prompted";
    private boolean requestRoleAfterMicrophone;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!preferences.getBoolean("wake_enabled", true)) {
            openJarvis();
            return;
        }

        RoleManager role = getSystemService(RoleManager.class);
        if (role == null || !role.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            openJarvis();
            return;
        }

        if (role.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            // A service can be selected before microphone permission is granted. In that case its
            // first passive-wake start fails. Acquire permission here and explicitly re-arm it.
            if (!hasMicrophonePermission()) {
                requestRoleAfterMicrophone = false;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_REQUEST);
            } else {
                JarvisVoiceInteractionService.refreshPassiveWakePreference();
                openJarvis();
            }
            return;
        }

        if (preferences.getBoolean(KEY_PROMPTED, false)) {
            openJarvis();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Enable JARVIS wake word")
                .setMessage("Wake word requires JARVIS as your Android assistant and microphone access. Android keeps the selected Voice Interaction Service available so JARVIS can listen locally for ‘Jarvis’ or ‘Hey Jarvis’. You can change this later in Settings.")
                .setPositiveButton("ENABLE WAKE WORD", (dialog, which) -> beginWakeSetup(role))
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

    private void beginWakeSetup(RoleManager role) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_PROMPTED, true).apply();
        if (!hasMicrophonePermission()) {
            requestRoleAfterMicrophone = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_REQUEST);
            return;
        }
        requestAssistantRole(role);
    }

    private boolean hasMicrophonePermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAssistantRole(RoleManager role) {
        try {
            startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), ASSISTANT_ROLE_REQUEST);
        } catch (RuntimeException failure) {
            Toast.makeText(this, "Android could not open Assistant selection. You can enable it from JARVIS Settings.", Toast.LENGTH_LONG).show();
            openJarvis();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MICROPHONE_REQUEST) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            Toast.makeText(this, "Microphone permission is required for voice and wake-word listening.", Toast.LENGTH_LONG).show();
            openJarvis();
            return;
        }

        if (requestRoleAfterMicrophone) {
            RoleManager role = getSystemService(RoleManager.class);
            if (role != null && role.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !role.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                requestAssistantRole(role);
                return;
            }
        }
        JarvisVoiceInteractionService.refreshPassiveWakePreference();
        openJarvis();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ASSISTANT_ROLE_REQUEST) {
            JarvisVoiceInteractionService.refreshPassiveWakePreference();
            openJarvis();
        }
    }

    private void openJarvis() {
        Intent launch = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(launch);
        finish();
    }
}
