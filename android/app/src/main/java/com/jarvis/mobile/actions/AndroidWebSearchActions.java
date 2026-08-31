package com.jarvis.mobile.actions;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.jarvis.mobile.brain.AndroidDefaultAppPreferencePersistence;

import java.util.Map;

/** Typed Android web search with a user-selected JARVIS browser and free browser fallback. */
public final class AndroidWebSearchActions {
    private final Context context;

    public AndroidWebSearchActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String search(String query) {
        String clean = query == null ? "" : query.trim();
        if (clean.isEmpty()) return "Tell me what you want me to search for.";
        try {
            String preferredBrowserPackage = preferredBrowserPackage();
            if (!preferredBrowserPackage.isBlank()) {
                Intent preferred = browserIntent(clean).setPackage(preferredBrowserPackage);
                if (preferred.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(preferred);
                    return "Searching for " + clean + ".";
                }
                new AndroidDefaultAppPreferencePersistence(context).remove("browser");
            }

            Intent nativeSearch = new Intent(Intent.ACTION_WEB_SEARCH)
                    .putExtra(SearchManager.QUERY, clean)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (nativeSearch.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(nativeSearch);
                return "Searching for " + clean + ".";
            }

            Intent browser = browserIntent(clean);
            if (browser.resolveActivity(context.getPackageManager()) == null) {
                return "No compatible web browser is available.";
            }
            context.startActivity(browser);
            return "Searching for " + clean + ".";
        } catch (SecurityException denied) {
            return "Android blocked web search because a required permission is off.";
        } catch (Exception unavailable) {
            return "No compatible web browser is available.";
        }
    }

    private String preferredBrowserPackage() {
        Map<String,String> preferences = new AndroidDefaultAppPreferencePersistence(context).load();
        String packageName = preferences.get("browser");
        return packageName == null ? "" : packageName.trim();
    }

    private static Intent browserIntent(String query) {
        return new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
