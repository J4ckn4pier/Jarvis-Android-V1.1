package com.jarvis.mobile.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Internal fidelity preview for Claude's canonical Developer-view export.
 *
 * This surface deliberately has no fallback/recreated UI. If the exact export is absent, it says so
 * rather than rendering an approximation. The production assistant remains on the existing native
 * shell until the exact artifact has been reviewed and accepted.
 */
public final class ClaudeUiPreviewActivity extends Activity {
    static final String CANONICAL_ASSET = "jarvis-live.html";
    private static final String CANONICAL_URL = "file:///android_asset/jarvis-live.html";
    static final String ANDROID_BRIDGE = "JarvisAndroid";

    private WebView preview;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!canonicalAssetExists()) {
            showMissingSourceState();
            return;
        }

        preview = new WebView(this);
        preview.setBackgroundColor(Color.BLACK);
        WebSettings settings = preview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        preview.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String target = request == null || request.getUrl() == null ? "" : request.getUrl().toString();
                return !CANONICAL_URL.equals(target);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !CANONICAL_URL.equals(url);
            }
        });
        preview.addJavascriptInterface(new ClaudeUiActionRouter(this), ANDROID_BRIDGE);
        setContentView(preview, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        preview.loadUrl(CANONICAL_URL);
    }

    @Override protected void onDestroy() {
        if (preview != null) {
            preview.removeJavascriptInterface(ANDROID_BRIDGE);
            preview.stopLoading();
            preview.loadUrl("about:blank");
            preview.clearHistory();
            preview.removeAllViews();
            preview.destroy();
            preview = null;
        }
        super.onDestroy();
    }

    private boolean canonicalAssetExists() {
        try (InputStream ignored = getAssets().open(CANONICAL_ASSET)) {
            return true;
        } catch (IOException missing) {
            return false;
        }
    }

    private void showMissingSourceState() {
        TextView message = new TextView(this);
        message.setText("Claude UI preview unavailable: the exact jarvis-live.html export is not packaged yet. No approximation has been substituted.");
        message.setTextColor(Color.WHITE);
        message.setBackgroundColor(Color.BLACK);
        message.setTextSize(16f);
        message.setGravity(Gravity.CENTER);
        int padding = Math.round(24f * getResources().getDisplayMetrics().density);
        message.setPadding(padding, padding, padding, padding);
        setContentView(message, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
