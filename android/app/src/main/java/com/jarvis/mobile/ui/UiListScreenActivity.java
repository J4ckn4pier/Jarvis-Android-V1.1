package com.jarvis.mobile.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jarvis.brain.UiListItem;
import com.jarvis.brain.UiSection;
import com.jarvis.mobile.R;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared "customizable list" screen: search box, item cards, add-row -> inline modal -> Save/Cancel,
 * "x" remove. This is the one reusable list pattern the canonical prototype uses across
 * Calendar/Messages/Browser/Tasks/Projects (see the 2026-08-28 "Full Asset Index" Slack canvas).
 * Concrete screens only declare which {@link UiSection} they render plus a couple of labels; all
 * reads/writes go through {@code ui.lists()}, the same store the brain itself uses.
 */
public abstract class UiListScreenActivity extends JarvisChromeActivity {
    private String query = "";

    protected abstract UiSection section();

    /** Singular noun for this screen's rows, e.g. "event", "contact", "bookmark", "task". */
    protected abstract String itemLabel();

    protected boolean showCheckbox() { return false; }

    protected String detailsHint() { return "Details"; }

    /** Extra content injected right after the search box (e.g. a Tasks/Projects tab switcher). */
    protected View extraHeader() { return null; }

    /** Hook for a screen-specific dialog action, e.g. Browser's "OPEN" neutral button. */
    protected void customizeDialog(AlertDialog.Builder builder, EditText detailsInput, UiListItem existing) { }

    /** Called when a row is tapped, before the default edit dialog. Return true to suppress it. */
    protected boolean onRowTap(UiListItem item) { return false; }

    @Override protected void buildBody(LinearLayout body) {
        body.addView(searchBox());
        View extra = extraHeader();
        if (extra != null) body.addView(extra);

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setPadding(0, dp(8), 0, dp(4));
        TextView add = new TextView(this);
        add.setText("+ ADD " + itemLabel().toUpperCase(Locale.ROOT));
        add.setTextColor(getColor(R.color.jarvis_cyan));
        add.setTextSize(14);
        add.setLetterSpacing(0.08f);
        add.setPadding(dp(6), dp(10), dp(6), dp(10));
        add.setOnClickListener(v -> showEditDialog(null));
        addRow.addView(add);
        body.addView(addRow);

        List<UiListItem> items = ui.lists().search(section(), query);
        if (items.isEmpty()) {
            body.addView(emptyState("No " + itemLabel() + "s yet."));
            return;
        }
        for (UiListItem item : items) body.addView(itemCard(item));
    }

    private EditText searchBox() {
        EditText search = new EditText(this);
        search.setHint("Search");
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(getColor(R.color.jarvis_text_faint));
        search.setBackgroundColor(getColor(R.color.jarvis_bg_panel));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setText(query);
        search.setSelection(query.length());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); }
            @Override public void afterTextChanged(Editable s) { render(); }
        });
        return search;
    }

    private LinearLayout itemCard(UiListItem item) {
        LinearLayout card = card();
        if (showCheckbox()) {
            CheckBox box = new CheckBox(this);
            box.setChecked(item.completed());
            box.setOnClickListener(v -> {
                ui.lists().setCompleted(section(), item.id(), box.isChecked());
                render();
            });
            card.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(item.title());
        title.setTextColor(item.completed() ? getColor(R.color.jarvis_text_dim) : Color.WHITE);
        title.setTextSize(16);
        if (item.completed()) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        copy.addView(title);
        if (!item.details().isEmpty()) {
            TextView details = new TextView(this);
            details.setText(item.details());
            details.setTextColor(getColor(R.color.jarvis_text_dim));
            details.setTextSize(13);
            details.setPadding(0, dp(3), 0, 0);
            copy.addView(details);
        }
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView remove = new TextView(this);
        remove.setText("×");
        remove.setTextColor(getColor(R.color.jarvis_danger));
        remove.setTextSize(22);
        remove.setPadding(dp(10), 0, dp(4), 0);
        remove.setContentDescription("Remove " + item.title());
        remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Remove " + itemLabel() + "?")
                .setMessage(item.title())
                .setPositiveButton("REMOVE", (d, w) -> {
                    ui.lists().remove(section(), item.id());
                    render();
                })
                .setNegativeButton("CANCEL", null)
                .show());
        card.addView(remove, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(v -> {
            if (!onRowTap(item)) showEditDialog(item);
        });
        return card;
    }

    private void showEditDialog(UiListItem existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));
        EditText titleInput = new EditText(this);
        titleInput.setHint("Title");
        titleInput.setSingleLine(true);
        EditText detailsInput = new EditText(this);
        detailsInput.setHint(detailsHint());
        if (existing != null) {
            titleInput.setText(existing.title());
            detailsInput.setText(existing.details());
        }
        form.addView(titleInput);
        form.addView(detailsInput);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle((existing == null ? "Add " : "Edit ") + itemLabel())
                .setView(form)
                .setPositiveButton("SAVE", (d, w) -> {
                    String title = titleInput.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(this, "Title required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String id = existing != null ? existing.id()
                            : (section().name().toLowerCase(Locale.ROOT) + "-" + System.currentTimeMillis());
                    boolean completed = existing != null && existing.completed();
                    ui.lists().upsert(section(), new UiListItem(id, title, detailsInput.getText().toString().trim(), completed, Map.of()));
                    render();
                })
                .setNegativeButton("CANCEL", null);
        customizeDialog(builder, detailsInput, existing);
        builder.show();
    }
}
