package com.jarvis.mobile;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jarvis.brain.EndpointTransportPolicy;
import com.jarvis.brain.SettingsStore;
import com.jarvis.mobile.brain.AndroidSharedPreferencesSettingsPersistence;
import com.jarvis.mobile.brain.providers.CortexProviderFactory;
import com.jarvis.mobile.brain.providers.SecureSecretStore;
import com.jarvis.mobile.remote.RemoteGoalStateStore;

/** Advanced/developer-only home for raw provider and remote-brain connection controls. */
public final class DeveloperSettingsActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences cortex = getSharedPreferences("jarvis_cortex", MODE_PRIVATE);
        SettingsStore research = new SettingsStore(new AndroidSharedPreferencesSettingsPersistence(this));
        SecureSecretStore secrets = new SecureSecretStore(this);
        RemoteGoalStateStore remote = new RemoteGoalStateStore(this);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(28), dp(20), dp(32));
        body.setBackgroundColor(getColor(R.color.jarvis_bg));
        body.addView(title("DEVELOPER OPTIONS"));
        body.addView(note("Advanced provider and endpoint controls. Normal JARVIS use does not require anything on this screen."));

        TextView status = note(CortexProviderFactory.status(this));
        body.addView(status);
        String selected = cortex.getString("mode", CortexProviderFactory.MODE_LOCAL);
        RadioGroup providers = new RadioGroup(this);
        RadioButton local = radio("Local / deterministic fallback", CortexProviderFactory.MODE_LOCAL.equals(selected));
        RadioButton localAi = radio("Local AI (Ollama-compatible)", CortexProviderFactory.MODE_LOCAL_AI.equals(selected));
        RadioButton compatible = radio("OpenAI-compatible endpoint", CortexProviderFactory.MODE_OPENAI_COMPATIBLE.equals(selected));
        RadioButton openai = radio("OpenAI", CortexProviderFactory.MODE_OPENAI.equals(selected));
        RadioButton anthropic = radio("Anthropic", CortexProviderFactory.MODE_ANTHROPIC.equals(selected));
        providers.addView(local); providers.addView(localAi); providers.addView(compatible); providers.addView(openai); providers.addView(anthropic);
        body.addView(providers);
        body.addView(note("Suggested local model: " + CortexProviderFactory.SUGGESTED_LOCAL_MODEL));
        body.addView(note("No API key is required for local AI. It runs on hardware you control; performance and capacity depend on that hardware."));

        String savedModel = cortex.getString("model", "");
        EditText model = input("Model name", savedModel);
        EditText endpoint = input("Provider endpoint", cortex.getString("endpoint", ""));
        EditText key = input("API key (leave blank to keep saved key)", "");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(model); body.addView(endpoint); body.addView(key);
        body.addView(button("SAVE PROVIDER CONFIG", () -> {
            String endpointValue = endpoint.getText().toString().trim();
            if (!endpointValue.isEmpty() && !EndpointTransportPolicy.allows(endpointValue)) {
                Toast.makeText(this, "Use HTTPS, loopback HTTP, or a user-owned .local HTTP endpoint.", Toast.LENGTH_LONG).show(); return;
            }
            String mode = providers.getCheckedRadioButtonId() == localAi.getId() ? CortexProviderFactory.MODE_LOCAL_AI
                    : providers.getCheckedRadioButtonId() == compatible.getId() ? CortexProviderFactory.MODE_OPENAI_COMPATIBLE
                    : providers.getCheckedRadioButtonId() == openai.getId() ? CortexProviderFactory.MODE_OPENAI
                    : providers.getCheckedRadioButtonId() == anthropic.getId() ? CortexProviderFactory.MODE_ANTHROPIC
                    : CortexProviderFactory.MODE_LOCAL;
            String modelValue = model.getText().toString().trim();
            if (CortexProviderFactory.MODE_LOCAL_AI.equals(mode) && modelValue.isEmpty()) {
                modelValue = CortexProviderFactory.SUGGESTED_LOCAL_MODEL;
                model.setText(modelValue);
            }
            String keyValue = key.getText().toString().trim();
            if (!keyValue.isEmpty() && !CortexProviderFactory.MODE_LOCAL_AI.equals(mode)) {
                try { secrets.put("provider_api_key", keyValue); key.setText(""); }
                catch (Exception error) { Toast.makeText(this, "Could not save that provider credential securely. Nothing was changed.", Toast.LENGTH_LONG).show(); return; }
            }
            cortex.edit().putString("mode", mode).putString("model", modelValue).putString("endpoint", endpointValue).apply();
            status.setText(CortexProviderFactory.status(this));
            Toast.makeText(this, CortexProviderFactory.MODE_LOCAL_AI.equals(mode)
                    ? "Local AI configuration saved. No API key is used."
                    : "Provider configuration saved.", Toast.LENGTH_SHORT).show();
        }));
        body.addView(button("CLEAR SAVED API KEY", () -> { secrets.remove("provider_api_key"); Toast.makeText(this, "Saved API key removed.", Toast.LENGTH_SHORT).show(); }));

        EditText researchEndpoint = input("Research endpoint", research.get(SettingsStore.RESEARCH_ENDPOINT));
        researchEndpoint.setContentDescription("JARVIS research endpoint");
        body.addView(researchEndpoint);
        Button saveResearch = button("SAVE RESEARCH ENDPOINT", () -> {
            String value = researchEndpoint.getText().toString().trim();
            if (!value.isEmpty() && !EndpointTransportPolicy.allows(value)) { Toast.makeText(this, "Use HTTPS, loopback HTTP, or a user-owned .local HTTP endpoint.", Toast.LENGTH_LONG).show(); return; }
            research.put(SettingsStore.RESEARCH_ENDPOINT, value);
            Toast.makeText(this, "Research endpoint saved.", Toast.LENGTH_SHORT).show();
        });
        saveResearch.setContentDescription("JARVIS save research endpoint");
        body.addView(saveResearch);

        RemoteGoalStateStore.Connection savedRemote = remote.loadConnection();
        EditText remoteEndpoint = input("Remote JARVIS endpoint", savedRemote == null ? "" : savedRemote.baseUrl());
        EditText remoteToken = input("Connection token", "");
        remoteToken.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(remoteEndpoint);
        body.addView(remoteToken);
        body.addView(button("SAVE REMOTE CONNECTION", () -> {
            String endpointValue = remoteEndpoint.getText().toString().trim();
            if (endpointValue.isEmpty() || !EndpointTransportPolicy.allows(endpointValue)) {
                Toast.makeText(this, "Use an approved HTTPS or local JARVIS endpoint.", Toast.LENGTH_LONG).show(); return;
            }
            String enteredToken = remoteToken.getText().toString();
            RemoteGoalStateStore.Connection existing = remote.loadConnection();
            String tokenToSave = enteredToken.isEmpty() && existing != null ? existing.token() : enteredToken;
            if (tokenToSave.isEmpty()) { Toast.makeText(this, "A connection token is required.", Toast.LENGTH_LONG).show(); return; }
            try {
                remote.saveConnection(endpointValue, tokenToSave);
                remoteToken.setText("");
                Toast.makeText(this, "Remote JARVIS connection saved securely.", Toast.LENGTH_SHORT).show();
            } catch (RuntimeException failure) {
                Toast.makeText(this, "Could not save the remote connection securely.", Toast.LENGTH_LONG).show();
            }
        }));
        body.addView(button("CLEAR REMOTE CONNECTION", () -> {
            remote.clearConnection();
            remoteToken.setText("");
            Toast.makeText(this, "Remote JARVIS connection removed.", Toast.LENGTH_SHORT).show();
        }));

        body.addView(button("RUN DIAGNOSTICS", () -> startActivity(new Intent(this, DiagnosticsActivity.class))));
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(getColor(R.color.jarvis_bg)); scroll.addView(body); setContentView(scroll);
    }

    private TextView title(String value) { TextView v = new TextView(this); v.setText(value); v.setTextColor(getColor(R.color.jarvis_cyan)); v.setTextSize(22); v.setPadding(0,0,0,dp(14)); return v; }
    private TextView note(String value) { TextView v = new TextView(this); v.setText(value); v.setTextColor(getColor(R.color.jarvis_text_dim)); v.setTextSize(15); v.setPadding(0,dp(4),0,dp(12)); return v; }
    private RadioButton radio(String value, boolean checked) { RadioButton b = new RadioButton(this); b.setId(android.view.View.generateViewId()); b.setText(value); b.setTextColor(getColor(R.color.jarvis_text_dim)); b.setChecked(checked); return b; }
    private EditText input(String hint, String value) { EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(getColor(R.color.jarvis_text_faint)); e.setTextColor(Color.WHITE); e.setText(value == null ? "" : value); e.setSingleLine(true); return e; }
    private Button button(String label, Runnable action) { Button b = new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setBackgroundColor(getColor(R.color.jarvis_cyan_dim)); b.setOnClickListener(v -> action.run()); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(0,dp(6),0,dp(6)); b.setLayoutParams(p); return b; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
