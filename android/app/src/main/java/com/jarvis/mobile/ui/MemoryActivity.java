package com.jarvis.mobile.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.jarvis.brain.MemoryType;
import com.jarvis.brain.RichMemory;
import com.jarvis.mobile.R;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Memory" screen: what JARVIS currently remembers, grouped by {@link MemoryType}, with manual
 * add/remove. Reads and writes only through {@code ui.memory()} / {@code ui.memoryEditor()} —
 * the same long-term memory store the brain itself consults, so nothing shown here can drift from
 * what the assistant actually knows.
 */
public final class MemoryActivity extends JarvisChromeActivity {
    private MemoryType filter; // null = all

    @Override protected String screenTitle() { return "MEMORY"; }

    @Override protected void buildBody(LinearLayout body) {
        List<RichMemory> active = new ArrayList<>();
        Instant now = Instant.now();
        for (RichMemory m : ui.memories()) {
            if (m.validAt(now) && (filter == null || m.type() == filter)) active.add(m);
        }

        body.addView(filterRow());

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setPadding(0, dp(8), 0, dp(4));
        TextView add = new TextView(this);
        add.setText("+ ADD MEMORY");
        add.setTextColor(getColor(R.color.jarvis_cyan));
        add.setTextSize(14);
        add.setLetterSpacing(0.08f);
        add.setPadding(dp(6), dp(10), dp(6), dp(10));
        add.setOnClickListener(v -> showAddDialog());
        addRow.addView(add);
        body.addView(addRow);

        if (active.isEmpty()) {
            body.addView(emptyState(filter == null ? "No memories yet." : "No " + filter.name().toLowerCase(Locale.ROOT) + " memories yet."));
            return;
        }

        for (RichMemory m : active) body.addView(memoryCard(m));
    }

    private LinearLayout filterRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(2));
        row.addView(filterChip("All", null));
        for (MemoryType type : MemoryType.values()) row.addView(filterChip(label(type), type));
        android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(this);
        scroller.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private TextView filterChip(String label, MemoryType type) {
        boolean selected = filter == type;
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(13);
        chip.setPadding(dp(14), dp(7), dp(14), dp(7));
        chip.setTextColor(selected ? Color.BLACK : getColor(R.color.jarvis_text_dim));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(selected ? getColor(R.color.jarvis_cyan) : getColor(R.color.jarvis_bg_panel));
        bg.setCornerRadius(dp(16));
        chip.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(p);
        chip.setOnClickListener(v -> { filter = type; render(); });
        return chip;
    }

    private LinearLayout memoryCard(RichMemory m) {
        LinearLayout card = card();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView content = new TextView(this);
        content.setText(m.content());
        content.setTextColor(Color.WHITE);
        content.setTextSize(16);
        TextView meta = new TextView(this);
        meta.setText(label(m.type()) + " · " + m.key());
        meta.setTextColor(getColor(R.color.jarvis_text_dim));
        meta.setTextSize(12);
        meta.setPadding(0, dp(3), 0, 0);
        copy.addView(content);
        copy.addView(meta);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView remove = new TextView(this);
        remove.setText("×");
        remove.setTextColor(getColor(R.color.jarvis_danger));
        remove.setTextSize(22);
        remove.setPadding(dp(10), 0, dp(4), 0);
        remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Remove memory?")
                .setMessage(m.content())
                .setPositiveButton("REMOVE", (d, w) -> {
                    ui.memoryEditor().remove(m.key(), Instant.now());
                    render();
                })
                .setNegativeButton("CANCEL", null)
                .show());
        card.addView(remove, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void showAddDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));

        Spinner typeSpinner = new Spinner(this);
        String[] labels = new String[MemoryType.values().length];
        for (int i = 0; i < labels.length; i++) labels[i] = label(MemoryType.values()[i]);
        typeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));

        EditText content = new EditText(this);
        content.setHint("What should JARVIS remember?");
        content.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        form.addView(typeSpinner);
        form.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("Add memory")
                .setView(form)
                .setPositiveButton("SAVE", (d, w) -> {
                    String text = content.getText().toString().trim();
                    if (text.isEmpty()) {
                        Toast.makeText(this, "Enter what to remember", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    MemoryType type = MemoryType.values()[Math.max(0, typeSpinner.getSelectedItemPosition())];
                    String key = "manual:" + type.name().toLowerCase(Locale.ROOT) + ":" + System.currentTimeMillis();
                    ui.addManualMemory(key, type, text, Instant.now());
                    render();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private static String label(MemoryType type) {
        String name = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
