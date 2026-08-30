package com.jarvis.mobile;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Applies the clean-room JARVIS Live visual layer to the existing production shell without
 * replacing or bypassing any of MainActivity's tested brain/action wiring.
 */
public final class JarvisApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static final int LIVE_ORB_TAG = 0x4a415256; // "JARV"

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof MainActivity) {
            activity.getWindow().getDecorView().post(() -> decorateMainActivity(activity));
        }
    }

    private void decorateMainActivity(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        View oldCore = findByDescription(root, "Speak to JARVIS");
        TextView response = asText(findByDescription(root, "JARVIS status and response"));
        if (oldCore == null || response == null || !(oldCore.getParent() instanceof ViewGroup)) return;

        ViewGroup parent = (ViewGroup) oldCore.getParent();
        JarvisLiveOrbView live = findLiveOrb(parent);
        if (live == null) {
            live = new JarvisLiveOrbView(activity);
            live.setTag(LIVE_ORB_TAG);
            // Keep the original invisible core in place so its already-tested tap/long-press
            // listeners remain the sole input owner. The visual layer itself never consumes input.
            live.setClickable(false);
            live.setFocusable(false);
            live.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ViewGroup.LayoutParams copied = copyLayoutParams(oldCore.getLayoutParams());
            int index = parent.indexOfChild(oldCore);
            parent.addView(live, index + 1, copied);
            oldCore.setAlpha(0f);
        }

        if (response.getTag(LIVE_ORB_TAG) == null) {
            final JarvisLiveOrbView target = live;
            response.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    target.setState(stateFor(s == null ? "" : s.toString()));
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            response.setTag(LIVE_ORB_TAG);
        }
        live.setState(stateFor(response.getText() == null ? "" : response.getText().toString()));
    }

    private static JarvisLiveOrbView.State stateFor(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        if (value.contains("listening") || value.contains("hearing")) return JarvisLiveOrbView.State.LISTENING;
        if (value.contains("processing") || value.startsWith("heard you say")) return JarvisLiveOrbView.State.THINKING;
        if (value.contains("approve") || value.contains("approval") || value.contains("confirm")) return JarvisLiveOrbView.State.ACTION_REQUIRED;
        if (value.contains("failed") || value.contains("error") || value.contains("unexpected problem")) return JarvisLiveOrbView.State.ERROR;
        if (!value.isBlank() && !value.contains("welcome sir") && !value.contains("tap the core")) return JarvisLiveOrbView.State.RESPONDING;
        return JarvisLiveOrbView.State.IDLE;
    }

    private static View findByDescription(View root, String description) {
        if (root == null) return null;
        CharSequence current = root.getContentDescription();
        if (current != null && description.contentEquals(current)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findByDescription(group.getChildAt(i), description);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static TextView asText(View view) { return view instanceof TextView ? (TextView) view : null; }

    private static JarvisLiveOrbView findLiveOrb(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof JarvisLiveOrbView && Integer.valueOf(LIVE_ORB_TAG).equals(child.getTag())) {
                return (JarvisLiveOrbView) child;
            }
        }
        return null;
    }

    private static ViewGroup.LayoutParams copyLayoutParams(ViewGroup.LayoutParams source) {
        if (source instanceof FrameLayout.LayoutParams) return new FrameLayout.LayoutParams((FrameLayout.LayoutParams) source);
        return new ViewGroup.LayoutParams(source.width, source.height);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
