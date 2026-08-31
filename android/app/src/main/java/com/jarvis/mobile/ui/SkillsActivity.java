package com.jarvis.mobile.ui;

import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jarvis.brain.ToolSpec;
import com.jarvis.brain.UiSection;
import com.jarvis.mobile.R;

import java.util.List;

/**
 * "Skills" screen. The canonical prototype describes this as "registered capabilities"; the
 * backend already distinguishes two real things that both belong here: the tools the brain has
 * actually registered ({@code ui.skills()} — read-only, since these are code, not user data) and
 * a user-manageable shortcuts list ({@code UiSection.SKILLS} via {@code ui.lists()}, which
 * {@code JarvisUiBackendTest} already exercises as ordinary editable-list data). Both are shown
 * rather than picking one and silently dropping the other.
 */
public final class SkillsActivity extends UiListScreenActivity {
    @Override protected String screenTitle() { return "SKILLS"; }
    @Override protected UiSection section() { return UiSection.SKILLS; }
    @Override protected String itemLabel() { return "shortcut"; }
    @Override protected String detailsHint() { return "Note"; }

    @Override protected void buildBody(LinearLayout body) {
        body.addView(section("REGISTERED CAPABILITIES"));
        List<ToolSpec> tools = ui.skills();
        if (tools.isEmpty()) {
            body.addView(emptyState("No capabilities registered."));
        } else {
            for (ToolSpec spec : tools) body.addView(toolCard(spec));
        }
        body.addView(section("MY SHORTCUTS"));
        super.buildBody(body);
    }

    private LinearLayout toolCard(ToolSpec spec) {
        LinearLayout card = card();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(spec.name().replace('_', ' '));
        name.setTextColor(Color.WHITE);
        name.setTextSize(16);
        copy.addView(name);
        if (!spec.description().isEmpty()) {
            TextView desc = new TextView(this);
            desc.setText(spec.description());
            desc.setTextColor(getColor(R.color.jarvis_text_dim));
            desc.setTextSize(13);
            desc.setPadding(0, dp(3), 0, 0);
            copy.addView(desc);
        }
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (spec.consequential()) {
            card.addView(pill("Approval", getColor(R.color.jarvis_cyan_dim), Color.WHITE));
        }
        return card;
    }
}
