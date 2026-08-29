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
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jarvis.mobile.brain.core.IntentPlan;
import com.jarvis.mobile.brain.core.LocalIntentEngine;
import com.jarvis.mobile.brain.providers.CortexProviderFactory;
import com.jarvis.mobile.hands.JarvisAccessibilityService;
import com.jarvis.mobile.memory.JarvisDatabase;

import java.util.LinkedHashMap;
import java.util.Map;

/** User-visible evidence of what the installed APK can actually see and run. */
public final class DiagnosticsActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("JARVIS Diagnostics");

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(28));
        body.setBackgroundColor(Color.rgb(3, 12, 17));
        body.addView(line("JARVIS PREFRONTAL CORTEX", true));
        body.addView(line(report(), false));

        ScrollView scroll = new ScrollView(this);
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
        add(report, "Bundled donor audio", "None (clean-room)");

        JarvisDatabase database = JarvisDatabase.get(this);
        add(report, "Saved memories", String.valueOf(database.memoryCount()));
        add(report, "Open tasks", String.valueOf(database.openTaskCount()));

        Map<String, IntentPlan.Intent> tests = new LinkedHashMap<>();
        tests.put("help me", IntentPlan.Intent.HELP);
        tests.put("could you call Mom please", IntentPlan.Intent.CALL);
        tests.put("send a text to Alex saying hello", IntentPlan.Intent.SMS);
        tests.put("schedule lunch tomorrow at 1 pm", IntentPlan.Intent.CALENDAR);
        tests.put("I need some light", IntentPlan.Intent.FLASHLIGHT_ON);
        tests.put("unmute the phone", IntentPlan.Intent.UNMUTE);
        tests.put("what did I miss", IntentPlan.Intent.NOTIFICATIONS);
        tests.put("what can you do", IntentPlan.Intent.HELP);
        tests.put("what is the capital of France", IntentPlan.Intent.KNOWLEDGE_QUERY);
        tests.put("read the screen", IntentPlan.Intent.ACCESSIBILITY);

        LocalIntentEngine engine = new LocalIntentEngine();
        int passed = 0;
        for (Map.Entry<String, IntentPlan.Intent> test : tests.entrySet()) {
            if (engine.plan(test.getKey()).intent() == test.getValue()) passed++;
        }
        add(report, "Installed language self-test", passed + "/" + tests.size() + " passed");
        report.append("\n\nDiagnostics do not place calls, send messages, or change the device.");
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
        view.setTextColor(heading ? Color.rgb(80, 225, 245) : Color.WHITE);
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
