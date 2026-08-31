package com.jarvis.mobile.ui;

import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;

import com.jarvis.brain.ActivityRecord;
import com.jarvis.mobile.R;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Activity" screen: the transparency/audit log described in the canonical prototype (Done /
 * Needs Input / Failed status pills). Read-only by design — this mirrors what the brain actually
 * recorded via {@code ui.activity()}; there is nothing here for a user to edit without that
 * becoming a second, disagreeing copy of the truth.
 */
public final class ActivityFeedActivity extends JarvisChromeActivity {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).withZone(ZoneId.systemDefault());

    @Override protected String screenTitle() { return "ACTIVITY"; }

    @Override protected void buildBody(LinearLayout body) {
        List<ActivityRecord> records = ui.activity().all();
        if (records.isEmpty()) {
            body.addView(emptyState("Nothing logged yet."));
            return;
        }
        for (ActivityRecord r : records) body.addView(recordCard(r));
    }

    private LinearLayout recordCard(ActivityRecord r) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.START);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(r.title());
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(statusPill(r.status()));
        card.addView(top);

        TextView when = new TextView(this);
        when.setText(WHEN.format(r.at()));
        when.setTextColor(getColor(R.color.jarvis_text_faint));
        when.setTextSize(12);
        when.setPadding(0, dp(2), 0, 0);
        card.addView(when);

        if (!r.detail().isEmpty()) {
            TextView detail = new TextView(this);
            detail.setText(r.detail());
            detail.setTextColor(getColor(R.color.jarvis_text_dim));
            detail.setTextSize(14);
            detail.setPadding(0, dp(6), 0, 0);
            card.addView(detail);
        }

        for (Map.Entry<String, String> e : r.evidence().entrySet()) {
            TextView evidence = new TextView(this);
            evidence.setText(e.getKey() + ": " + e.getValue());
            evidence.setTextColor(getColor(R.color.jarvis_text_faint));
            evidence.setTextSize(12);
            evidence.setPadding(0, dp(2), 0, 0);
            card.addView(evidence);
        }
        return card;
    }

    private TextView statusPill(ActivityRecord.Status status) {
        switch (status) {
            case DONE:
                return pill("Done", getColor(R.color.jarvis_cyan), Color.BLACK);
            case NEEDS_APPROVAL:
                return pill("Needs Approval", getColor(R.color.jarvis_cyan_dim), Color.WHITE);
            case NEEDS_DECISION:
                return pill("Needs Input", getColor(R.color.jarvis_cyan_dim), Color.WHITE);
            case FAILED:
                return pill("Failed", getColor(R.color.jarvis_danger), Color.BLACK);
            case IGNORED:
            default:
                return pill("Ignored", getColor(R.color.jarvis_bg_panel_raised), getColor(R.color.jarvis_text_dim));
        }
    }
}
