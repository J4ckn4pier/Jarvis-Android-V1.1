package com.jarvis.mobile.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jarvis.brain.MusicQueueStore;
import com.jarvis.brain.MusicTrack;
import com.jarvis.mobile.R;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * "Music" screen: Now Playing transport plus queue, matching the canonical prototype's Music
 * screen. Backed entirely by {@code ui.music()} — the actual audio transport stays
 * platform-owned elsewhere (see {@code MusicQueueStore}'s own doc comment); this screen is the
 * queue/playback-state surface, the same one a voice command like "skip this song" updates.
 */
public final class MusicActivity extends JarvisChromeActivity {
    private String query = "";

    @Override protected String screenTitle() { return "MUSIC"; }

    @Override protected void buildBody(LinearLayout body) {
        body.addView(nowPlaying());
        body.addView(section("QUEUE"));
        body.addView(searchAndAdd());

        MusicQueueStore.PlaybackState state = ui.music().state();
        List<MusicTrack> queue = ui.music().queue();
        List<MusicTrack> visible = query.isBlank() ? queue : queue.stream()
                .filter(t -> (t.title() + " " + t.artist()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        if (visible.isEmpty()) {
            body.addView(emptyState(queue.isEmpty() ? "Queue is empty." : "No matches."));
            return;
        }
        for (MusicTrack track : visible) body.addView(trackRow(track, state.current() != null && state.current().id().equals(track.id())));
    }

    private LinearLayout nowPlaying() {
        MusicQueueStore.PlaybackState state = ui.music().state();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(getColor(R.color.jarvis_bg_panel));
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));

        TextView title = new TextView(this);
        title.setText(state.current() == null ? "Nothing queued" : state.current().title());
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        TextView artist = new TextView(this);
        artist.setText(state.current() == null ? "" : state.current().artist());
        artist.setTextColor(getColor(R.color.jarvis_text_dim));
        artist.setTextSize(14);
        panel.addView(title);
        panel.addView(artist);

        long duration = state.current() == null ? 0 : state.current().durationSeconds();
        SeekBar seek = new SeekBar(this);
        seek.setMax((int) Math.max(1, duration));
        seek.setProgress((int) Math.min(state.positionSeconds(), Math.max(1, duration)));
        seek.setEnabled(state.current() != null);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { ui.music().seek(seekBar.getProgress()); render(); }
        });
        panel.addView(seek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout transport = new LinearLayout(this);
        transport.setOrientation(LinearLayout.HORIZONTAL);
        transport.setGravity(Gravity.CENTER);
        transport.setPadding(0, dp(8), 0, dp(4));
        transport.addView(transportButton(state.shuffle() ? "⤨" : "⇄", v -> { ui.music().setShuffle(!state.shuffle()); render(); }, state.shuffle()));
        transport.addView(transportButton("⏮", v -> { ui.music().previous(); render(); }, false));
        transport.addView(transportButton(state.playing() ? "⏸" : "▶", v -> { ui.music().togglePlay(); render(); }, false));
        transport.addView(transportButton("⏭", v -> { ui.music().next(); render(); }, false));
        transport.addView(transportButton(state.repeat() ? "🔁" : "↻", v -> { ui.music().setRepeat(!state.repeat()); render(); }, state.repeat()));
        panel.addView(transport);

        LinearLayout volumeRow = new LinearLayout(this);
        volumeRow.setOrientation(LinearLayout.HORIZONTAL);
        volumeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView volumeLabel = new TextView(this);
        volumeLabel.setText("🔊");
        volumeLabel.setTextColor(getColor(R.color.jarvis_text_dim));
        volumeRow.addView(volumeLabel, new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT));
        SeekBar volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(state.volume());
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { if (fromUser) ui.music().setVolume(progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        volumeRow.addView(volume, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(volumeRow);
        return panel;
    }

    private TextView transportButton(String symbol, android.view.View.OnClickListener onClick, boolean active) {
        TextView v = new TextView(this);
        v.setText(symbol);
        v.setTextSize(26);
        v.setPadding(dp(14), dp(6), dp(14), dp(6));
        v.setTextColor(active ? getColor(R.color.jarvis_cyan) : Color.WHITE);
        v.setOnClickListener(onClick);
        return v;
    }

    private LinearLayout searchAndAdd() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        EditText search = new EditText(this);
        search.setHint("Search queue");
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(getColor(R.color.jarvis_text_faint));
        search.setBackgroundColor(getColor(R.color.jarvis_bg_panel));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setText(query);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); }
            @Override public void afterTextChanged(Editable s) { render(); }
        });
        row.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView add = new TextView(this);
        add.setText("+ ADD");
        add.setTextColor(getColor(R.color.jarvis_cyan));
        add.setTextSize(14);
        add.setPadding(dp(14), dp(10), dp(6), dp(10));
        add.setOnClickListener(v -> showAddTrackDialog());
        row.addView(add);
        return row;
    }

    private LinearLayout trackRow(MusicTrack track, boolean isCurrent) {
        LinearLayout card = card();
        if (isCurrent) card.setBackgroundColor(getColor(R.color.jarvis_bg_panel_raised));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(track.title());
        title.setTextColor(isCurrent ? getColor(R.color.jarvis_cyan) : Color.WHITE);
        title.setTextSize(16);
        TextView artist = new TextView(this);
        artist.setText(track.artist() + "  ·  " + format(track.durationSeconds()));
        artist.setTextColor(getColor(R.color.jarvis_text_dim));
        artist.setTextSize(13);
        artist.setPadding(0, dp(3), 0, 0);
        copy.addView(title);
        copy.addView(artist);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView remove = new TextView(this);
        remove.setText("×");
        remove.setTextColor(getColor(R.color.jarvis_danger));
        remove.setTextSize(22);
        remove.setPadding(dp(10), 0, dp(4), 0);
        remove.setOnClickListener(v -> { ui.music().remove(track.id()); render(); });
        card.addView(remove, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(v -> { ui.music().play(track.id()); render(); });
        return card;
    }

    private void showAddTrackDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));
        EditText titleInput = new EditText(this);
        titleInput.setHint("Track title");
        EditText artistInput = new EditText(this);
        artistInput.setHint("Artist");
        EditText durationInput = new EditText(this);
        durationInput.setHint("Duration (seconds)");
        durationInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        form.addView(titleInput);
        form.addView(artistInput);
        form.addView(durationInput);

        new AlertDialog.Builder(this)
                .setTitle("Add track")
                .setView(form)
                .setPositiveButton("SAVE", (d, w) -> {
                    String title = titleInput.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(this, "Title required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long duration = 0;
                    try { duration = Long.parseLong(durationInput.getText().toString().trim()); } catch (NumberFormatException ignored) { }
                    String id = "track-" + System.currentTimeMillis();
                    ui.music().add(new MusicTrack(id, title, artistInput.getText().toString().trim(), Math.max(0, duration)));
                    render();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private static String format(long seconds) {
        long m = seconds / 60, s = seconds % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }
}
