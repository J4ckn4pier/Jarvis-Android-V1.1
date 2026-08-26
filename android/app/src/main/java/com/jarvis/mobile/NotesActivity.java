package com.jarvis.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jarvis.mobile.memory.JarvisDatabase;

/** Working replacement for the donor Notes screen, backed by JARVIS memory. */
public class NotesActivity extends Activity {
    private LinearLayout notes;
    private JarvisDatabase database;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Jarvis Notes");
        database = JarvisDatabase.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.setBackgroundColor(Color.rgb(245, 248, 249));

        Button add = new Button(this);
        add.setText("ADD NOTE");
        add.setTextColor(Color.WHITE);
        add.setBackgroundColor(Color.rgb(0, 124, 150));
        add.setOnClickListener(v -> addNoteDialog());
        root.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        notes = new LinearLayout(this);
        notes.setOrientation(LinearLayout.VERTICAL);
        notes.setPadding(0, dp(12), 0, dp(16));
        scroll.addView(notes);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        if (notes != null) refresh();
    }

    private void refresh() {
        notes.removeAllViews();
        String memories = database.recentMemories(50);
        String tasks = database.openTasks(30);
        if (memories.isEmpty() && tasks.isEmpty()) {
            TextView empty = text("No Notes!\n\nSay “remember that…” or tap ADD NOTE.", 18, Color.DKGRAY);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(60), dp(10), dp(30));
            notes.addView(empty, fullWrap());
            return;
        }
        if (!memories.isEmpty()) addSection("MEMORY", memories);
        if (!tasks.isEmpty()) addSection("OPEN TASKS", tasks);
    }

    private void addSection(String heading, String value) {
        TextView title = text(heading, 13, Color.rgb(0, 115, 140));
        title.setPadding(dp(4), dp(8), dp(4), dp(6));
        notes.addView(title, fullWrap());
        TextView body = text(value, 16, Color.rgb(30, 35, 38));
        body.setTextIsSelectable(true);
        body.setBackgroundColor(Color.WHITE);
        body.setPadding(dp(12), dp(12), dp(12), dp(12));
        notes.addView(body, fullWrap());
    }

    private void addNoteDialog() {
        EditText input = new EditText(this);
        input.setHint("What should I remember?");
        input.setMinLines(3);
        new AlertDialog.Builder(this)
                .setTitle("New Jarvis Note")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        database.remember("note " + System.currentTimeMillis(), value);
                        refresh();
                    }
                })
                .show();
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
