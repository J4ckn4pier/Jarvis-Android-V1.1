package com.jarvis.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jarvis.brain.EndpointTransportPolicy;
import com.jarvis.brain.ExternalResearchGateway;
import com.jarvis.brain.SettingsStore;
import com.jarvis.brain.ToolRegistry;
import com.jarvis.mobile.brain.AndroidSharedPreferencesSettingsPersistence;
import com.jarvis.mobile.brain.AndroidToolRegistryFactory;
import com.jarvis.mobile.brain.providers.CortexProviderFactory;
import com.jarvis.mobile.hands.JarvisAccessibilityService;
import com.jarvis.mobile.memory.JarvisDatabase;

/** User-visible evidence of what the installed APK can actually see and run. */
public final class DiagnosticsActivity extends Activity {
    private static final String[] ANDROID_TYPED_TOOLS = {
            "open_dialer", "call_contact", "open_app", "web_search", "set_timer", "set_alarm",
            "navigate", "device_navigation", "screen_read", "ui_click", "ui_type", "media_play",
            "media_control", "volume_control", "set_flashlight", "calendar_query", "create_reminder",
            "compose_calendar_event", "notification_query", "compose_email", "send_message"
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("JARVIS Diagnostics");

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(28));
        body.setBackgroundColor(getColor(R.color.jarvis_bg));
        body.addView(line("JARVIS PREFRONTAL CORTEX", true));
        TextView report = line(report(), false);
        report.setBackgroundColor(getColor(R.color.jarvis_bg_panel));
        report.setPadding(dp(12), dp(16), dp(12), dp(16));
        body.addView(report);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.jarvis_bg));
        scroll.addView(body);
        setContentView(scroll);
    }

    private String report() {
        StringBuilder report = new StringBuilder();
        add(report, "Package", getPackageName());
        add(report, "Version", version());
        add(report, "Device", Build.MANUFACTURER + " " + Build.MODEL);
        add(report, "Android", Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT);
        add(report, "Speech recognizer", yes(SpeechRecognizer.isRecognitionAvailable(this)));
        add(report, "On-device recognizer", Build.VERSION.SDK_INT >= 31
                ? yes(SpeechRecognizer.isOnDeviceRecognitionAvailable(this))
                : "Not exposed by this Android version");
        add(report, "Microphone", permission(Manifest.permission.RECORD_AUDIO));
        add(report, "Contacts", permission(Manifest.permission.READ_CONTACTS));
        add(report, "Calls", permission(Manifest.permission.CALL_PHONE));
        add(report, "Camera / flashlight", permission(Manifest.permission.CAMERA));
        add(report, "Notifications", Build.VERSION.SDK_INT >= 33
                ? permission(Manifest.permission.POST_NOTIFICATIONS) : "Granted by Android version");
        add(report, "Notification awareness", yes(notificationListenerEnabled()));
        add(report, "Device Control", yes(accessibilityEnabled()));
        add(report, "Reasoning mode", CortexProviderFactory.status(this));

        SettingsStore settings = new SettingsStore(new AndroidSharedPreferencesSettingsPersistence(this));
        String researchEndpoint = settings.get(SettingsStore.RESEARCH_ENDPOINT).trim();
        String researchStatus = researchEndpoint.isEmpty()
                ? "Not configured"
                : EndpointTransportPolicy.allows(researchEndpoint) ? "Configured" : "Blocked by transport policy";
        add(report, "Live research", researchStatus);
        add(report, "Bundled donor audio", "None (clean-room)");

        JarvisDatabase database = JarvisDatabase.get(this);
        add(report, "Saved memories", String.valueOf(database.memoryCount()));
        add(report, "Open tasks", String.valueOf(database.openTaskCount()));

        ToolRegistry registry = AndroidToolRegistryFactory.create(this, ExternalResearchGateway.unavailable());
        int registered = 0;
        for (String tool : ANDROID_TYPED_TOOLS) {
            if (registry.resolve(tool).isPresent()) registered++;
        }
        add(report, "Production typed-tool self-test", registered + "/" + ANDROID_TYPED_TOOLS.length + " registered");
        add(report, "Total shared capabilities", String.valueOf(registry.specs().size()));
        add(report, "Autonomous conversational calls", "External duplex phone-audio transport required");
        report.append("\n\nDiagnostics inspect capability registration only. They do not place calls, send messages, click controls, or change the device.");
        return report.toString();
    }

    private String version() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName + " (" +
                    (Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode) + ")";
        } catch (Exception error) {
            return "Unavailable";
        }
    }

    private String permission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ? "Granted" : "Not granted";
    }

    private boolean notificationListenerEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private boolean accessibilityEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String component = new ComponentName(this, JarvisAccessibilityService.class).flattenToString();
        return enabled != null && enabled.contains(component);
    }

    private TextView line(String text, boolean heading) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(heading ? getColor(R.color.jarvis_cyan) : getColor(R.color.jarvis_text_dim));
        view.setTextSize(heading ? 22 : 15);
        view.setPadding(0, heading ? 0 : dp(16), 0, 0);
        view.setLineSpacing(0, 1.18f);
        return view;
    }

    private void add(StringBuilder report, String label, String value) {
        if (report.length() > 0) report.append('\n');
        report.append(label).append(": ").append(value);
    }

    private String yes(boolean value) { return value ? "Available" : "Unavailable"; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
