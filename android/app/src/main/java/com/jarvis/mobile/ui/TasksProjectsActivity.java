package com.jarvis.mobile.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jarvis.brain.UiSection;
import com.jarvis.mobile.R;

/**
 * "Tasks & Projects" screen. The canonical prototype describes this as one screen with
 * Today/Upcoming tabs; the backend that already exists (see {@code JarvisUiBackendTest}) models
 * Tasks and Projects as two distinct {@link UiSection}s rather than a date split, so this screen
 * tabs between those two real backend sections instead of inventing a due-date field the brain
 * doesn't have yet.
 */
public final class TasksProjectsActivity extends UiListScreenActivity {
    private UiSection active = UiSection.TASKS;

    @Override protected String screenTitle() { return "TASKS & PROJECTS"; }
    @Override protected UiSection section() { return active; }
    @Override protected String itemLabel() { return active == UiSection.TASKS ? "task" : "project"; }
    @Override protected boolean showCheckbox() { return true; }
    @Override protected String detailsHint() { return "Notes"; }

    @Override protected View extraHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        row.addView(tab("Tasks", UiSection.TASKS));
        row.addView(tab("Projects", UiSection.PROJECTS));
        return row;
    }

    private TextView tab(String label, UiSection section) {
        boolean selected = active == section;
        TextView tab = new TextView(this);
        tab.setText(label);
        tab.setTextSize(14);
        tab.setGravity(android.view.Gravity.CENTER);
        tab.setPadding(0, dp(10), 0, dp(10));
        tab.setTextColor(selected ? Color.BLACK : getColor(R.color.jarvis_text_dim));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? getColor(R.color.jarvis_cyan) : getColor(R.color.jarvis_bg_panel));
        bg.setCornerRadius(dp(8));
        tab.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(0, 0, dp(6), 0);
        tab.setLayoutParams(p);
        tab.setOnClickListener(v -> { active = section; render(); });
        return tab;
    }
}
