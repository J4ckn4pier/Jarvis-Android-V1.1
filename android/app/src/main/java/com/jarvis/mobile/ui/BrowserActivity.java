package com.jarvis.mobile.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.EditText;
import android.widget.Toast;

import com.jarvis.brain.UiListItem;
import com.jarvis.brain.UiSection;

/** "Browser" screen: customizable bookmark list, per the canonical prototype's Browser screen. */
public final class BrowserActivity extends UiListScreenActivity {
    @Override protected String screenTitle() { return "BROWSER"; }
    @Override protected UiSection section() { return UiSection.BROWSER; }
    @Override protected String itemLabel() { return "bookmark"; }
    @Override protected String detailsHint() { return "URL"; }

    @Override protected void customizeDialog(AlertDialog.Builder builder, EditText detailsInput, UiListItem existing) {
        if (existing == null) return;
        builder.setNeutralButton("OPEN", (d, w) -> openUrl(existing.details()));
    }

    private void openUrl(String url) {
        String clean = url == null ? "" : url.trim();
        if (clean.isEmpty()) {
            Toast.makeText(this, "No URL saved for this bookmark", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!clean.contains("://")) clean = "https://" + clean;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(clean)));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open that link", Toast.LENGTH_SHORT).show();
        }
    }
}
