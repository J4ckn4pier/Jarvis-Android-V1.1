package com.jarvis.mobile.ui;

import android.webkit.WebView;

import org.json.JSONObject;

/**
 * Presentation-only channel from Android into Claude's exact HTML surface.
 *
 * The publisher intentionally knows nothing about wake detection, reasoning, tools, or the
 * orchestrator. Owning production components decide which truthful presentation state applies and
 * pass it across this boundary; the canonical UI listens for the stable {@code jarvis:state}
 * browser event without requiring Android to know Claude's DOM structure.
 */
public final class ClaudeUiStatePublisher {
    static final String STATE_EVENT = "jarvis:state";
    static final String IDLE = "idle";
    static final String LISTENING = "listening";
    static final String THINKING = "thinking";
    static final String RESPONDING = "responding";
    static final String ACTING = "acting";

    public enum State {
        IDLE(ClaudeUiStatePublisher.IDLE),
        LISTENING(ClaudeUiStatePublisher.LISTENING),
        THINKING(ClaudeUiStatePublisher.THINKING),
        RESPONDING(ClaudeUiStatePublisher.RESPONDING),
        ACTING(ClaudeUiStatePublisher.ACTING);

        private final String wireValue;

        State(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    private final WebView webView;

    public ClaudeUiStatePublisher(WebView webView) {
        if (webView == null) {
            throw new IllegalArgumentException("webView is required");
        }
        this.webView = webView;
    }

    public void publish(State state) {
        publish(state, "");
    }

    public void publish(State state, String message) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        String safeState = JSONObject.quote(state.wireValue());
        String safeMessage = JSONObject.quote(message == null ? "" : message);
        String script = "(function(){window.dispatchEvent(new CustomEvent("
                + JSONObject.quote(STATE_EVENT)
                + ",{detail:{state:" + safeState + ",message:" + safeMessage + "}}));})();";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }
}
