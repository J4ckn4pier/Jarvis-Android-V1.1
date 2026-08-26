package com.jarvis.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Donor Help & Features screen, updated for the transplanted command system. */
public class CommandsActivity extends Activity {
    private static final String[] COMMANDS = {
            "Call Mom", "Dial 555 0100", "Text Alex saying I’m on my way",
            "Email Jordan subject Update saying the build is ready",
            "Schedule lunch with Maria tomorrow at 1 PM", "Set a timer for 10 minutes",
            "Set an alarm for 7:30 AM", "Open Spotify", "Search for nearby coffee",
            "Take me to the airport", "Turn on the flashlight", "Volume up",
            "Play / Pause / Next / Previous", "Read my notifications",
            "Remember that Deadworld launches in October", "What do you remember about Deadworld?",
            "Add task buy groceries", "List tasks", "Complete task 1",
            "Read the screen", "Tap Continue", "Type hello world", "Scroll down", "Back", "Home"
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Help & Features");
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(18), dp(18), dp(28));
        body.setBackgroundColor(Color.rgb(3, 12, 17));

        TextView title = label("JARVIS COMMANDS", 24, Color.rgb(80, 225, 245));
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        body.addView(title, fullWrap());

        TextView hint = label("Tap the reactor and speak naturally. Long-press it to type.",
                14, Color.WHITE);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(8), 0, dp(18));
        body.addView(hint, fullWrap());

        for (String command : COMMANDS) {
            TextView row = label("›  " + command, 16, Color.rgb(210, 245, 250));
            row.setBackgroundColor(Color.rgb(5, 31, 40));
            row.setPadding(dp(12), dp(13), dp(12), dp(13));
            LinearLayout.LayoutParams params = fullWrap();
            params.setMargins(0, 0, 0, dp(2));
            body.addView(row, params);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        setContentView(scroll);
    }

    private TextView label(String value, float size, int color) {
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
