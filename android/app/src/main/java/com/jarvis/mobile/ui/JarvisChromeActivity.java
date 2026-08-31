package com.jarvis.mobile.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jarvis.brain.JarvisUiBackend;
import com.jarvis.mobile.R;
import com.jarvis.mobile.brain.AndroidBrainRuntime;

/**
 * Shared chrome for the JARVIS screens that render the canonical prototype's remaining surfaces
 * (Memory, Tasks & Projects, Routines, Activity, Calendar, Messages, Devices, Music, Browser,
 * Skills, Overlays, Connect) through {@link JarvisUiBackend} rather than inventing a second
 * source of truth. Deliberately mirrors SettingsActivity's existing dark-panel/cyan-accent
 * conventions (same toolbar shape, same card/row look, same AlertDialog-based modal pattern)
 * instead of introducing a new visual language, and uses only plain android.widget views to match
 * this module's existing no-AndroidX, no-third-party-dependency footprint.
 */
public abstract class JarvisChromeActivity extends Activity {
    protected AndroidBrainRuntime runtime;
    protected JarvisUiBackend ui;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        runtime = new AndroidBrainRuntime(this);
        ui = runtime.uiBackend();
        getWindow().setStatusBarColor(getColor(R.color.jarvis_bg));
        getWindow().setNavigationBarColor(getColor(R.color.jarvis_bg));
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (ui != null) render();
    }

    /** Rebuilds the whole screen from current backend state. Call after any mutation. */
    protected void render() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(getColor(R.color.jarvis_bg));
        page.addView(toolbar(screenTitle()), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(8), dp(18), dp(40));
        body.setBackgroundColor(getColor(R.color.jarvis_bg));
        buildBody(body);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.jarvis_bg));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
    }

    /** Title shown in the toolbar, e.g. "MEMORY". */
    protected abstract String screenTitle();

    /** Populate the scrolling body. Called by {@link #render()} every time state changes. */
    protected abstract void buildBody(LinearLayout body);

    protected View toolbar(String title) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(18), 0);
        bar.setBackgroundColor(getColor(R.color.jarvis_bg));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(getColor(R.color.jarvis_cyan));
        back.setTextSize(38);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(20);
        titleView.setLetterSpacing(0.16f);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return bar;
    }

    protected TextView section(String title) {
        TextView v = new TextView(this);
        v.setText(title);
        v.setTextColor(getColor(R.color.jarvis_cyan));
        v.setTextSize(12);
        v.setLetterSpacing(0.12f);
        v.setPadding(dp(6), dp(22), dp(6), dp(8));
        return v;
    }

    protected TextView emptyState(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(getColor(R.color.jarvis_text_faint));
        v.setTextSize(14);
        v.setPadding(dp(6), dp(18), dp(6), dp(18));
        return v;
    }

    protected LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(13), dp(12), dp(13));
        card.setBackgroundColor(getColor(R.color.jarvis_bg_panel));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(2));
        card.setLayoutParams(p);
        card.setMinimumHeight(dp(68));
        return card;
    }

    /** Navigation row (title + subtitle + chevron) used by the hub and by simple status rows. */
    protected View row(String title, String subtitle, Runnable action) {
        LinearLayout card = card();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(17);
        TextView detail = new TextView(this);
        detail.setText(subtitle);
        detail.setTextColor(getColor(R.color.jarvis_text_dim));
        detail.setTextSize(13);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(name);
        copy.addView(detail);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (action != null) {
            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextColor(getColor(R.color.jarvis_cyan));
            arrow.setTextSize(28);
            arrow.setGravity(Gravity.CENTER);
            card.addView(arrow, new LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.MATCH_PARENT));
            card.setContentDescription(title + ". " + subtitle);
            card.setOnClickListener(v -> action.run());
        }
        return card;
    }

    /** Small colored status badge, e.g. for Activity/Connect status pills. */
    protected TextView pill(String text, int backgroundColor, int textColor) {
        TextView v = new TextView(this);
        v.setText(text.toUpperCase(java.util.Locale.ROOT));
        v.setTextColor(textColor);
        v.setTextSize(11);
        v.setLetterSpacing(0.08f);
        v.setPadding(dp(10), dp(4), dp(10), dp(4));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(backgroundColor);
        bg.setCornerRadius(dp(10));
        v.setBackground(bg);
        return v;
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
