package com.jarvis.mobile.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jarvis.brain.ConnectionState;
import com.jarvis.brain.ConnectionType;
import com.jarvis.mobile.R;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * "Connect" screen: third-party account linking, per the canonical prototype's Connect screen.
 * Backed by {@code ui.connections()} ({@code ConnectionRegistry}), which by design only tracks
 * non-secret connection state — actual OAuth handshakes and credential storage are a separate,
 * not-yet-built piece of work, so the toggle here flips real registry state honestly rather than
 * pretending to run a login flow it doesn't have.
 */
public final class ConnectActivity extends JarvisChromeActivity {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).withZone(ZoneId.systemDefault());

    @Override protected String screenTitle() { return "CONNECT"; }

    @Override protected void buildBody(LinearLayout body) {
        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setPadding(0, dp(8), 0, dp(4));
        TextView add = new TextView(this);
        add.setText("+ ADD CONNECTION");
        add.setTextColor(getColor(R.color.jarvis_cyan));
        add.setTextSize(14);
        add.setLetterSpacing(0.08f);
        add.setPadding(dp(6), dp(10), dp(6), dp(10));
        add.setOnClickListener(v -> showAddDialog());
        addRow.addView(add);
        body.addView(addRow);

        List<ConnectionState> connections = ui.connections().all();
        if (connections.isEmpty()) {
            body.addView(emptyState("No connections registered yet."));
            return;
        }
        for (ConnectionState c : connections) body.addView(connectionCard(c));
    }

    private LinearLayout connectionCard(ConnectionState c) {
        LinearLayout card = card();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(c.id());
        name.setTextColor(Color.WHITE);
        name.setTextSize(16);
        TextView detail = new TextView(this);
        detail.setText(label(c.type()) + (c.connected() && c.authenticatedAt() != null ? "  ·  connected " + WHEN.format(c.authenticatedAt()) : "  ·  not connected"));
        detail.setTextColor(getColor(R.color.jarvis_text_dim));
        detail.setTextSize(13);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(name);
        copy.addView(detail);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(c.connected());
        toggle.setContentDescription(c.id() + " connected toggle");
        toggle.setOnCheckedChangeListener((b, checked) -> {
            if (checked) ui.connections().markConnected(c.id(), Instant.now());
            else ui.connections().disconnect(c.id());
            render();
        });
        card.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void showAddDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));

        EditText idInput = new EditText(this);
        idInput.setHint("Service name (e.g. spotify, google-calendar)");
        String[] labels = new String[ConnectionType.values().length];
        for (int i = 0; i < labels.length; i++) labels[i] = label(ConnectionType.values()[i]);
        Spinner typeSpinner = new Spinner(this);
        typeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        form.addView(idInput);
        form.addView(typeSpinner);

        new AlertDialog.Builder(this)
                .setTitle("Add connection")
                .setView(form)
                .setPositiveButton("SAVE", (d, w) -> {
                    String id = idInput.getText().toString().trim();
                    if (id.isEmpty()) {
                        Toast.makeText(this, "Service name required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ConnectionType type = ConnectionType.values()[Math.max(0, typeSpinner.getSelectedItemPosition())];
                    ui.connections().register(id, type);
                    render();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private static String label(ConnectionType type) {
        switch (type) {
            case NATIVE_ANDROID: return "Native Android integration";
            case WEB_OAUTH: return "Web account (OAuth)";
            case AI_PROVIDER: return "AI provider";
            default: return type.name();
        }
    }
}
