package com.jarvis.mobile.ui;

import android.content.Intent;
import android.graphics.Color;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jarvis.brain.PopupOverlayAction;
import com.jarvis.brain.PopupOverlayOutcome;
import com.jarvis.brain.PopupOverlayState;
import com.jarvis.mobile.R;

/** Shows only overlay behavior that is production-backed in this build. */
public final class OverlaysActivity extends JarvisChromeActivity {
    @Override protected String screenTitle() { return "OVERLAYS"; }

    @Override protected void buildBody(LinearLayout body) {
        body.addView(section("POPUP OVERLAY — LIVE"));
        body.addView(popupDemo());

        body.addView(section("ANDROID SURFACES"));
        body.addView(row("Screen Controls", "Open Android Accessibility settings", () -> launch(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        body.addView(row("Notification Access", "Open Android notification-listener settings", () -> launch(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        body.addView(row("Lock Screen", "Open Android display settings", () -> launch(Settings.ACTION_DISPLAY_SETTINGS)));
    }

    private LinearLayout popupDemo() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(getColor(R.color.jarvis_bg_panel));
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));

        PopupOverlayState state = ui.popup().state();
        if (!state.visible()) {
            TextView hint = new TextView(this);
            hint.setText("No popup is currently showing.");
            hint.setTextColor(getColor(R.color.jarvis_text_dim));
            hint.setTextSize(14);
            panel.addView(hint);

            TextView trigger = new TextView(this);
            trigger.setText("SHOW TEST POPUP");
            trigger.setTextColor(getColor(R.color.jarvis_cyan));
            trigger.setTextSize(14);
            trigger.setLetterSpacing(0.08f);
            trigger.setPadding(dp(6), dp(14), dp(6), dp(4));
            trigger.setOnClickListener(v -> {
                ui.popup().show("Reservation available", "Table for two, 7:00 PM. Book it?");
                render();
            });
            panel.addView(trigger);
            return panel;
        }

        TextView title = new TextView(this);
        title.setText(state.title());
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        panel.addView(title);

        if (state.expanded() || !state.detail().isEmpty()) {
            TextView detail = new TextView(this);
            detail.setText(state.detail());
            detail.setTextColor(getColor(R.color.jarvis_text_dim));
            detail.setTextSize(14);
            detail.setPadding(0, dp(4), 0, dp(8));
            panel.addView(detail);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(popupAction("LATER", PopupOverlayAction.LATER));
        actions.addView(popupAction("DETAILS", PopupOverlayAction.DETAILS));
        actions.addView(popupAction("YES", PopupOverlayAction.YES));
        panel.addView(actions);
        return panel;
    }

    private TextView popupAction(String label, PopupOverlayAction action) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextColor(action == PopupOverlayAction.YES ? Color.BLACK : Color.WHITE);
        v.setTextSize(14);
        v.setGravity(android.view.Gravity.CENTER);
        v.setPadding(dp(6), dp(10), dp(6), dp(10));
        if (action == PopupOverlayAction.YES) {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(getColor(R.color.jarvis_cyan));
            bg.setCornerRadius(dp(6));
            v.setBackground(bg);
        }
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        v.setLayoutParams(p);
        v.setOnClickListener(view -> {
            PopupOverlayOutcome outcome = ui.popup().onAction(action);
            String message = switch (outcome) {
                case DEFERRED -> "Deferred.";
                case SHOW_DETAILS -> "Showing details.";
                case APPROVAL_REQUESTED -> "Approval requested by the popup controller.";
            };
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            render();
        });
        return v;
    }

    private void launch(String action) {
        try { startActivity(new Intent(action)); } catch (Exception e) { Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show(); }
    }
}
