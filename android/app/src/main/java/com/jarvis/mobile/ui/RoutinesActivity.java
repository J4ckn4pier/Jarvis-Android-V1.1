package com.jarvis.mobile.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;

import com.jarvis.brain.Plan;
import com.jarvis.brain.PlanStep;
import com.jarvis.brain.RoutineDefinition;
import com.jarvis.mobile.R;

import java.util.List;
import java.util.Map;

/**
 * "Routines" screen: user-programmable when/then automations, backed by {@code ui.routines()}.
 * The canonical prototype's five example routines mix several action types (text someone, add a
 * calendar/tasks entry, dim lights); the composer here starts with the one action every routine
 * example shares a variant of — sending a message — since that is the tool the shared brain
 * already executes end to end ({@code send_message}, required args recipient/message). Enabling a
 * routine here uses the exact same {@code RoutineStore} the brain consults when matching a trigger
 * phrase, so a routine toggled off here is genuinely not executable, not just hidden.
 */
public final class RoutinesActivity extends JarvisChromeActivity {
    @Override protected String screenTitle() { return "ROUTINES"; }

    @Override protected void buildBody(LinearLayout body) {
        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setPadding(0, dp(8), 0, dp(4));
        TextView add = new TextView(this);
        add.setText("+ ADD ROUTINE");
        add.setTextColor(getColor(R.color.jarvis_cyan));
        add.setTextSize(14);
        add.setLetterSpacing(0.08f);
        add.setPadding(dp(6), dp(10), dp(6), dp(10));
        add.setOnClickListener(v -> showAddDialog());
        addRow.addView(add);
        body.addView(addRow);

        List<RoutineDefinition> routines = ui.routines().all();
        if (routines.isEmpty()) {
            body.addView(emptyState("No routines yet."));
            return;
        }
        for (RoutineDefinition r : routines) body.addView(routineCard(r));
    }

    private LinearLayout routineCard(RoutineDefinition r) {
        LinearLayout card = card();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(r.title());
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        TextView detail = new TextView(this);
        detail.setText("When: " + summarizeTrigger(r) + "  →  Then: " + summarizeAction(r));
        detail.setTextColor(getColor(R.color.jarvis_text_dim));
        detail.setTextSize(13);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(title);
        copy.addView(detail);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(r.enabled());
        toggle.setContentDescription(r.title() + " enabled toggle");
        toggle.setOnCheckedChangeListener((button, checked) -> {
            ui.routines().setEnabled(r.id(), checked);
            render();
        });
        card.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView remove = new TextView(this);
        remove.setText("×");
        remove.setTextColor(getColor(R.color.jarvis_danger));
        remove.setTextSize(22);
        remove.setPadding(dp(10), 0, dp(4), 0);
        remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Remove routine?")
                .setMessage(r.title())
                .setPositiveButton("REMOVE", (d, w) -> { ui.routines().remove(r.id()); render(); })
                .setNegativeButton("CANCEL", null)
                .show());
        card.addView(remove, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void showAddDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));

        EditText titleInput = new EditText(this);
        titleInput.setHint("Routine name (e.g. \"Leaving work\")");
        EditText triggerInput = new EditText(this);
        triggerInput.setHint("Trigger phrase (e.g. \"I'm leaving work\")");
        EditText recipientInput = new EditText(this);
        recipientInput.setHint("Send message to");
        EditText messageInput = new EditText(this);
        messageInput.setHint("Message to send");

        form.addView(titleInput);
        form.addView(triggerInput);
        form.addView(recipientInput);
        form.addView(messageInput);

        new AlertDialog.Builder(this)
                .setTitle("Add routine")
                .setMessage("This composer builds \"when I say a phrase, send a message\" routines — the action type every canonical example routine includes a variant of. Other action types route through the same RoutineStore and can be added the same way later.")
                .setView(form)
                .setPositiveButton("SAVE", (d, w) -> {
                    String title = titleInput.getText().toString().trim();
                    String trigger = triggerInput.getText().toString().trim();
                    String recipient = recipientInput.getText().toString().trim();
                    String message = messageInput.getText().toString().trim();
                    if (title.isEmpty() || trigger.isEmpty() || recipient.isEmpty() || message.isEmpty()) {
                        Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String id = "routine-" + System.currentTimeMillis();
                    Plan plan = new Plan("Text " + recipient,
                            List.of(new PlanStep("send_message", Map.of("recipient", recipient, "message", message), true)));
                    RoutineDefinition routine = new RoutineDefinition(id, title, "phrase", Map.of("phrase", trigger), plan, true);
                    ui.routines().upsert(routine);
                    render();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private static String summarizeTrigger(RoutineDefinition r) {
        String phrase = r.triggerArguments().get("phrase");
        return phrase == null || phrase.isBlank() ? r.triggerType() : "“" + phrase + "”";
    }

    private static String summarizeAction(RoutineDefinition r) {
        List<PlanStep> steps = r.actionPlan().steps();
        if (steps.isEmpty()) return r.actionPlan().goal();
        PlanStep first = steps.get(0);
        if ("send_message".equals(first.tool())) {
            return "text " + first.arguments().getOrDefault("recipient", "someone");
        }
        return r.actionPlan().goal();
    }
}
