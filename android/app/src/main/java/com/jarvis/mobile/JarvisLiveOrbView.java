package com.jarvis.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Original clean-room JARVIS Live orb renderer.
 *
 * The live Claude prototype uses layered cyan HUD rings and changes presentation with the
 * assistant's real state. This Android view intentionally owns no scripted scenario timeline:
 * callers set a state only when the actual microphone/runtime state changes.
 */
public final class JarvisLiveOrbView extends View {
    public enum State { IDLE, LISTENING, THINKING, RESPONDING, ACTION_REQUIRED, ERROR }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private State state = State.IDLE;
    private float phase;

    public JarvisLiveOrbView(Context context) {
        super(context);
        setContentDescription("JARVIS live assistant core. Idle.");
        setFocusable(true);
        setClickable(true);
    }

    public void setState(State value) {
        state = value == null ? State.IDLE : value;
        setContentDescription("JARVIS live assistant core. " + label(state) + ".");
        invalidate();
    }

    public State state() { return state; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.36f;
        int accent = accent(state);
        float pulse = state == State.IDLE ? 0f : (float) ((Math.sin(phase) + 1d) * 0.5d);

        // Subtle clean-room HUD field behind the core.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(28, Color.red(accent), Color.green(accent), Color.blue(accent)));
        canvas.drawCircle(cx, cy, radius * (1.15f + pulse * 0.05f), paint);

        // Outer segmented targeting rings, inspired by the canonical prototype's radial language.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.25f));
        paint.setColor(withAlpha(accent, 92));
        RectF outer = new RectF(cx - radius * 1.18f, cy - radius * 1.18f,
                cx + radius * 1.18f, cy + radius * 1.18f);
        for (int i = 0; i < 8; i++) {
            float start = -90f + i * 45f + phase * (state == State.THINKING ? 2.3f : 0.35f);
            canvas.drawArc(outer, start, 24f, false, paint);
        }

        paint.setStrokeWidth(dp(2f));
        paint.setColor(withAlpha(accent, 155));
        canvas.drawCircle(cx, cy, radius * 0.92f, paint);

        paint.setStrokeWidth(dp(1f));
        paint.setColor(withAlpha(accent, 88));
        canvas.drawCircle(cx, cy, radius * 0.72f, paint);

        // Inner energy core.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(accent, 34));
        canvas.drawCircle(cx, cy, radius * (0.58f + pulse * 0.025f), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3f));
        paint.setColor(withAlpha(accent, 220));
        canvas.drawCircle(cx, cy, radius * 0.48f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(accent, 210));
        canvas.drawCircle(cx, cy, radius * (0.18f + pulse * 0.025f), paint);

        // Small cardinal ticks make the core read as a HUD instrument rather than a blue dot.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2f));
        paint.setColor(withAlpha(accent, 185));
        float tickIn = radius * 1.02f;
        float tickOut = radius * 1.13f;
        canvas.drawLine(cx, cy - tickIn, cx, cy - tickOut, paint);
        canvas.drawLine(cx, cy + tickIn, cx, cy + tickOut, paint);
        canvas.drawLine(cx - tickIn, cy, cx - tickOut, cy, paint);
        canvas.drawLine(cx + tickIn, cy, cx + tickOut, cy, paint);

        if (state != State.IDLE) {
            phase += state == State.THINKING ? 0.22f : 0.12f;
            postInvalidateDelayed(48L);
        }
    }

    private int accent(State state) {
        switch (state) {
            case ACTION_REQUIRED: return Color.rgb(255, 190, 92);
            case ERROR: return Color.rgb(255, 143, 143);
            case RESPONDING: return Color.rgb(103, 232, 173);
            case LISTENING: return Color.rgb(85, 214, 255);
            case THINKING: return Color.rgb(143, 130, 255);
            case IDLE:
            default: return Color.rgb(85, 214, 255);
        }
    }

    private static String label(State state) {
        switch (state) {
            case LISTENING: return "Listening";
            case THINKING: return "Thinking";
            case RESPONDING: return "Responding";
            case ACTION_REQUIRED: return "Action required";
            case ERROR: return "Error";
            case IDLE:
            default: return "Idle";
        }
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
