package com.jarvis.mobile;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jarvis.brain.SettingsStore;
import com.jarvis.mobile.brain.AndroidSharedPreferencesSettingsPersistence;
import com.jarvis.mobile.brain.providers.CortexProviderFactory;
import com.jarvis.mobile.brain.providers.SecureSecretStore;

/** JARVIS settings surface with private, provider-neutral cortex configuration. */
public class SettingsActivity extends Activity {
    private SharedPreferences preferences;
    private SharedPreferences cortexPreferences;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("JARVIS Settings");
        preferences = getSharedPreferences("jarvis_shell", MODE_PRIVATE);
        cortexPreferences = getSharedPreferences("jarvis_cortex", MODE_PRIVATE);
        SettingsStore researchSettings = new SettingsStore(new AndroidSharedPreferencesSettingsPersistence(this));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(24));
        body.setBackgroundColor(getColor(R.color.jarvis_bg));

        body.addView(header("GENERAL"), fullWrap());
        body.addView(toggle("Voice responses", "voice_enabled", true), fullWrap());

        body.addView(header("PREFRONTAL CORTEX"), fullWrap());
        TextView cortexStatus = new TextView(this);
        cortexStatus.setText(CortexProviderFactory.status(this));
        cortexStatus.setTextColor(getColor(R.color.jarvis_text_dim));
        cortexStatus.setPadding(dp(8), dp(4), dp(8), dp(8));
        body.addView(cortexStatus, fullWrap());

        String selectedProvider = cortexPreferences.getString("mode", CortexProviderFactory.MODE_LOCAL);
        RadioGroup providers = new RadioGroup(this);
        RadioButton local = radio("Private deterministic executive (default)", CortexProviderFactory.MODE_LOCAL.equals(selectedProvider));
        RadioButton compatible = radio("OpenAI-compatible local endpoint (free/self-hosted)", CortexProviderFactory.MODE_OPENAI_COMPATIBLE.equals(selectedProvider));
        RadioButton openAi = radio("Optional OpenAI Responses cortex", CortexProviderFactory.MODE_OPENAI.equals(selectedProvider));
        RadioButton anthropic = radio("Optional Anthropic Messages cortex", CortexProviderFactory.MODE_ANTHROPIC.equals(selectedProvider));
        providers.addView(local);
        providers.addView(compatible);
        providers.addView(openAi);
        providers.addView(anthropic);
        body.addView(providers, fullWrap());

        EditText model = textSetting("Model name", cortexPreferences.getString("model", ""));
        body.addView(model, fullWrap());
        EditText endpoint = textSetting("Optional endpoint (HTTPS, loopback HTTP, or user-owned .local HTTP)", cortexPreferences.getString("endpoint", ""));
        body.addView(endpoint, fullWrap());
        EditText apiKey = textSetting("API key (optional for local endpoint; blank keeps saved key)", "");
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        SecureSecretStore secrets = new SecureSecretStore(this);
        if (!secrets.get("provider_api_key").isEmpty()) apiKey.setHint("API key saved securely");
        body.addView(apiKey, fullWrap());

        body.addView(action("SAVE CORTEX SETTINGS", () -> {
            String provider = providers.getCheckedRadioButtonId() == compatible.getId()
                    ? CortexProviderFactory.MODE_OPENAI_COMPATIBLE
                    : providers.getCheckedRadioButtonId() == openAi.getId()
                    ? CortexProviderFactory.MODE_OPENAI
                    : providers.getCheckedRadioButtonId() == anthropic.getId()
                    ? CortexProviderFactory.MODE_ANTHROPIC
                    : CortexProviderFactory.MODE_LOCAL;
            cortexPreferences.edit()
                    .putString("mode", provider)
                    .putString("model", model.getText().toString().trim())
                    .putString("endpoint", endpoint.getText().toString().trim())
                    .apply();
            try {
                if (!apiKey.getText().toString().trim().isEmpty()) {
                    secrets.put("provider_api_key", apiKey.getText().toString());
                    apiKey.setText("");
                    apiKey.setHint("API key saved securely");
                }
                cortexStatus.setText(CortexProviderFactory.status(this));
                Toast.makeText(this, "Cortex settings saved.", Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(this, "Android Keystore could not save that key.", Toast.LENGTH_LONG).show();
            }
        }), fullWrap());
        body.addView(action("CLEAR SAVED API KEY", () -> {
            secrets.remove("provider_api_key");
            apiKey.setText("");
            apiKey.setHint("API key cleared; optional for local endpoint");
            cortexStatus.setText(CortexProviderFactory.status(this));
            Toast.makeText(this, "Saved cortex API key removed.", Toast.LENGTH_SHORT).show();
        }), fullWrap());
        body.addView(action("RUN JARVIS DIAGNOSTICS", () -> startActivity(new Intent(this, DiagnosticsActivity.class))), fullWrap());

        body.addView(header("LIVE RESEARCH"), fullWrap());
        TextView researchHelp = new TextView(this);
        researchHelp.setText("Research endpoint: optional user-owned service JARVIS can query for fresh information. Use HTTPS, loopback HTTP, or a trusted device on your own network such as http://jarvis-research.local. Leave blank to keep live research disabled rather than silently using a paid provider.");
        researchHelp.setTextColor(getColor(R.color.jarvis_text_dim));
        researchHelp.setPadding(dp(8), dp(4), dp(8), dp(8));
        body.addView(researchHelp, fullWrap());
        EditText researchEndpoint = textSetting("Research endpoint", researchSettings.get(SettingsStore.RESEARCH_ENDPOINT));
        body.addView(researchEndpoint, fullWrap());
        body.addView(action("SAVE RESEARCH ENDPOINT", () -> {
            researchSettings.put(SettingsStore.RESEARCH_ENDPOINT, researchEndpoint.getText().toString().trim());
            Toast.makeText(this, researchEndpoint.getText().toString().trim().isEmpty()
                    ? "Live research endpoint cleared."
                    : "Live research endpoint saved.", Toast.LENGTH_SHORT).show();
        }), fullWrap());

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
        body.addView(action("ENABLE NOTIFICATION AWARENESS", () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))), fullWrap());
        body.addView(action("ENABLE DEVICE CONTROL", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), fullWrap());

        TextView privacy = new TextView(this);
        privacy.setText("Private by default: memories remain in the app’s local database and the deterministic executive handles recognized phone actions without a cloud model. You can optionally connect a self-hosted OpenAI-compatible endpoint (for example a local model server) without a paid-provider key. If you explicitly enable any external provider, unresolved requests and questions may be sent to that endpoint. No API credential is embedded in the APK. Superseded third-party advertising, analytics, licensing, visual, and speech payloads are not included.");
        privacy.setTextColor(getColor(R.color.jarvis_text_dim));
        privacy.setPadding(0, dp(20), 0, 0);
        body.addView(privacy, fullWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        setContentView(scroll);
    }

    private void requestAssistant() {
        RoleManager manager = getSystemService(RoleManager.class);
        if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            startActivity(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT));
        }
    }

    private TextView header(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(13);
        view.setTextColor(getColor(R.color.jarvis_cyan));
        view.setPadding(0, dp(18), 0, dp(6));
        return view;
    }

    private Switch toggle(String title, String key, boolean defaultValue) {
        Switch toggle = new Switch(this);
        toggle.setText(title);
        toggle.setTextColor(getColor(R.color.jarvis_text_dim));
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
        button.setTextColor(getColor(R.color.jarvis_text_dim));
        button.setTextSize(17);
        button.setChecked(checked);
        return button;
    }

    private Button action(String title, Runnable runnable) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(getColor(R.color.jarvis_cyan_dim));
        button.setOnClickListener(v -> runnable.run());
        LinearLayout.LayoutParams params = fullWrap();
        params.setMargins(0, dp(4), 0, dp(4));
        button.setLayoutParams(params);
        return button;
    }

    private EditText textSetting(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(getColor(R.color.jarvis_text_faint));
        input.setTextColor(getColor(R.color.jarvis_text_dim));
        input.setText(value == null ? "" : value);
        input.setSingleLine(true);
        input.setPadding(dp(8), dp(8), dp(8), dp(8));
        return input;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}