package com.jarvis.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jarvis.mobile.assistant.JarvisVoiceInteractionService;
import com.jarvis.mobile.brain.providers.CortexProviderFactory;
import com.jarvis.mobile.brain.providers.SecureSecretStore;

/** Canonical user-facing JARVIS Settings. Raw endpoint/provider fields live in DeveloperSettingsActivity. */
public class SettingsActivity extends Activity {
    private static final String ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";
    private SharedPreferences preferences;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("jarvis_shell", MODE_PRIVATE);
        getWindow().setStatusBarColor(getColor(R.color.jarvis_bg));
        getWindow().setNavigationBarColor(getColor(R.color.jarvis_bg));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(getColor(R.color.jarvis_bg));
        page.addView(toolbar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(8), dp(18), dp(40));
        body.setBackgroundColor(getColor(R.color.jarvis_bg));

        body.addView(section("VOICE & INVOCATION"));
        body.addView(toggleRow("Voice", "Speak responses aloud", "voice_enabled", true));
        body.addView(toggleRow("Wake Word", "Listen for “Jarvis” or “Hey Jarvis”", "wake_enabled", true));
        body.addView(row("Voice Model", "Android system voice", () -> launch(ACTION_TTS_SETTINGS)));
        body.addView(row("Language", getResources().getConfiguration().getLocales().get(0).getDisplayLanguage(), () -> launch(Settings.ACTION_LOCALE_SETTINGS)));

        body.addView(section("JARVIS & APPS"));
        body.addView(row("App Permissions", "Microphone, contacts, calendar and device access", () -> launchAppDetails()));
        body.addView(row("AI Providers", providerSummary(), this::showProviderConnections));
        body.addView(row("Backup & Sync", "Local-first memory backup", () -> Toast.makeText(this, "JARVIS currently keeps memory local on this device.", Toast.LENGTH_SHORT).show()));
        body.addView(row("Profile", preferences.getString("profile_name", "Sir"), () -> Toast.makeText(this, "Profile personalization is active.", Toast.LENGTH_SHORT).show()));
        body.addView(row("Default Apps", "Choose Android defaults", () -> launch(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)));
        body.addView(row("Personality", preferences.getString("personality_label", "Humble Butler"), () -> Toast.makeText(this, "JARVIS personality: " + preferences.getString("personality_label", "Humble Butler"), Toast.LENGTH_SHORT).show()));
        body.addView(row("Widgets & Lock Screen", "Assistant access and display options", () -> launch(Settings.ACTION_DISPLAY_SETTINGS)));

        body.addView(section("ANDROID INTEGRATION"));
        body.addView(row("Default Assistant", assistantSummary(), this::requestAssistant));
        body.addView(row("Notifications", "Allow JARVIS to understand notification context", () -> launch(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        body.addView(row("Screen Controls", "Accessibility-powered screen reading and navigation", () -> launch(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        body.addView(section("ADVANCED"));
        body.addView(row("Developer Options", "Provider endpoints, models and diagnostics", () -> startActivity(new Intent(this, DeveloperSettingsActivity.class))));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.jarvis_bg));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
    }

    private View toolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(18), 0);
        bar.setBackgroundColor(getColor(R.color.jarvis_bg));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(getColor(R.color.jarvis_cyan));
        back.setTextSize(38);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("SETTINGS");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setLetterSpacing(0.16f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return bar;
    }

    private TextView section(String title) {
        TextView v = new TextView(this);
        v.setText(title);
        v.setTextColor(getColor(R.color.jarvis_cyan));
        v.setTextSize(12);
        v.setLetterSpacing(0.12f);
        v.setPadding(dp(6), dp(22), dp(6), dp(8));
        return v;
    }

    private View row(String title, String subtitle, Runnable action) {
        LinearLayout card = card();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(17);
        TextView detail = new TextView(this);
        detail.setText(subtitle);
        detail.setTextColor(getColor(R.color.jarvis_text_dim));
        detail.setTextSize(13);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(name);
        copy.addView(detail);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(getColor(R.color.jarvis_cyan));
        arrow.setTextSize(28);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.MATCH_PARENT));
        card.setContentDescription(title + ". " + subtitle);
        card.setOnClickListener(v -> action.run());
        return card;
    }

    private View toggleRow(String title, String subtitle, String key, boolean defaultValue) {
        LinearLayout card = card();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this); name.setText(title); name.setTextColor(Color.WHITE); name.setTextSize(17);
        TextView detail = new TextView(this); detail.setText(subtitle); detail.setTextColor(getColor(R.color.jarvis_text_dim)); detail.setTextSize(13); detail.setPadding(0,dp(3),0,0);
        copy.addView(name); copy.addView(detail);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(preferences.getBoolean(key, defaultValue));
        toggle.setContentDescription(title + " toggle");
        toggle.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(key, checked).apply();
            if ("wake_enabled".equals(key)) JarvisVoiceInteractionService.refreshPassiveWakePreference();
        });
        card.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(13), dp(12), dp(13));
        card.setBackgroundColor(getColor(R.color.jarvis_bg_panel));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(2));
        card.setLayoutParams(p);
        card.setMinimumHeight(dp(68));
        return card;
    }

    private String providerSummary() {
        String status = CortexProviderFactory.status(this).toLowerCase(java.util.Locale.ROOT);
        if (status.contains("anthropic")) return "Anthropic connected";
        if (status.contains("openai")) return "OpenAI-compatible provider connected";
        return "Private local mode";
    }

    private void showProviderConnections() {
        new AlertDialog.Builder(this)
                .setTitle("AI Providers")
                .setMessage(providerSummary() + "\n\nJARVIS stays usable in private local mode. Advanced connection details are kept out of normal Settings.")
                .setPositiveButton("CONNECT / CHANGE", (dialog, which) -> startActivity(new Intent(this, DeveloperSettingsActivity.class)))
                .setNegativeButton("DISCONNECT", (dialog, which) -> disconnectProvider())
                .setNeutralButton("CANCEL", null)
                .show();
    }

    private void disconnectProvider() {
        getSharedPreferences("jarvis_cortex", MODE_PRIVATE).edit()
                .putString("mode", CortexProviderFactory.MODE_LOCAL)
                .apply();
        new SecureSecretStore(this).remove("provider_api_key");
        Toast.makeText(this, "External AI provider disconnected. JARVIS is using private local mode.", Toast.LENGTH_SHORT).show();
        recreate();
    }

    private String assistantSummary() {
        RoleManager manager = getSystemService(RoleManager.class);
        return manager != null && manager.isRoleHeld(RoleManager.ROLE_ASSISTANT) ? "JARVIS is the default assistant" : "Set JARVIS as the default assistant";
    }

    private void requestAssistant() {
        RoleManager manager = getSystemService(RoleManager.class);
        if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            startActivity(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT));
        }
    }

    private void launchAppDetails() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }
    private void launch(String action) {
        try { startActivity(new Intent(action)); }
        catch (Exception ignored) { Toast.makeText(this, "That Android settings page is not available on this device.", Toast.LENGTH_SHORT).show(); }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
