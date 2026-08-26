package com.jarvis.mobile;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Clean implementation of the donor General/Notifications/Quick Activation settings. */
public class SettingsActivity extends Activity {
    private SharedPreferences preferences;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("JARVIS Settings");
        preferences = getSharedPreferences("jarvis_shell", MODE_PRIVATE);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(24));
        body.setBackgroundColor(Color.rgb(244, 247, 248));

        body.addView(header("GENERAL"), fullWrap());
        body.addView(toggle("Voice responses", "voice_enabled", true), fullWrap());
        body.addView(toggle("Legacy JARVIS audio cues", "legacy_cues", true), fullWrap());

        body.addView(header("MARK THEME"), fullWrap());
        RadioGroup themes = new RadioGroup(this);
        RadioButton mark3 = radio("Mark III", "mk3".equals(preferences.getString("mark_theme", "mk3")));
        RadioButton mark2 = radio("Mark II", "mk2".equals(preferences.getString("mark_theme", "mk3")));
        themes.addView(mark3);
        themes.addView(mark2);
        themes.setOnCheckedChangeListener((group, checkedId) -> preferences.edit()
                .putString("mark_theme", checkedId == mark2.getId() ? "mk2" : "mk3").apply());
        body.addView(themes, fullWrap());

        body.addView(header("OPERATING MODE"), fullWrap());
        RadioGroup modes = new RadioGroup(this);
        String currentMode = preferences.getString("operating_mode", "normal");
        RadioButton normal = radio("Normal", "normal".equals(currentMode));
        RadioButton quiet = radio("Quiet mode", "quiet".equals(currentMode));
        RadioButton office = radio("Office mode", "office".equals(currentMode));
        modes.addView(normal);
        modes.addView(quiet);
        modes.addView(office);
        modes.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = checkedId == quiet.getId() ? "quiet" : checkedId == office.getId() ? "office" : "normal";
            preferences.edit().putString("operating_mode", mode).apply();
        });
        body.addView(modes, fullWrap());

        body.addView(header("ANDROID INTEGRATION"), fullWrap());
        body.addView(action("MAKE JARVIS DEFAULT ASSISTANT", this::requestAssistant), fullWrap());
        body.addView(action("ENABLE NOTIFICATION AWARENESS", () ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))), fullWrap());
        body.addView(action("ENABLE DEVICE CONTROL", () ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), fullWrap());

        TextView privacy = new TextView(this);
        privacy.setText("Private beta: memories remain in the app’s local SQLite database. " +
                "The obsolete donor advertising, analytics, licensing, and legacy speech payloads are not included.");
        privacy.setTextColor(Color.DKGRAY);
        privacy.setPadding(0, dp(20), 0, 0);
        body.addView(privacy, fullWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        setContentView(scroll);
    }

    private void requestAssistant() {
        RoleManager manager = getSystemService(RoleManager.class);
        if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
                !manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            startActivity(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT));
        }
    }

    private TextView header(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(13);
        view.setTextColor(Color.rgb(0, 115, 140));
        view.setPadding(0, dp(18), 0, dp(6));
        return view;
    }

    private Switch toggle(String title, String key, boolean defaultValue) {
        Switch toggle = new Switch(this);
        toggle.setText(title);
        toggle.setTextSize(17);
        toggle.setPadding(dp(8), dp(10), dp(8), dp(10));
        toggle.setChecked(preferences.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, checked) -> preferences.edit().putBoolean(key, checked).apply());
        return toggle;
    }

    private RadioButton radio(String title, boolean checked) {
        RadioButton button = new RadioButton(this);
        button.setId(android.view.View.generateViewId());
        button.setText(title);
        button.setTextSize(17);
        button.setChecked(checked);
        return button;
    }

    private Button action(String title, Runnable runnable) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(0, 118, 144));
        button.setOnClickListener(v -> runnable.run());
        LinearLayout.LayoutParams params = fullWrap();
        params.setMargins(0, dp(4), 0, dp(4));
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
