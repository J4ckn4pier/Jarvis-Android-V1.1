package com.jarvis.mobile.actions;

import com.jarvis.mobile.hands.JarvisAccessibilityService;

import java.util.Locale;

/** Safe typed navigation/read-only accessibility actions for the production tool registry. */
public final class AndroidAccessibilityActions {
    public String navigate(String action) {
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        boolean completed = switch (normalized) {
            case "back", "go back" -> JarvisAccessibilityService.back();
            case "home", "go home" -> JarvisAccessibilityService.home();
            case "scroll down", "down", "scroll forward" -> JarvisAccessibilityService.scrollForward();
            case "scroll up", "up", "scroll backward" -> JarvisAccessibilityService.scrollBackward();
            default -> false;
        };
        if (!isSupported(normalized)) return "Unsupported device navigation action: " + normalized;
        if (!completed) return "Enable JARVIS Device Control in Accessibility settings first.";
        return switch (normalized) {
            case "back", "go back" -> "Went back.";
            case "home", "go home" -> "Went home.";
            case "scroll down", "down", "scroll forward" -> "Scrolled down.";
            default -> "Scrolled up.";
        };
    }

    public String readScreen() {
        String text = JarvisAccessibilityService.screenText();
        if (text == null || text.isBlank()) {
            return "Enable JARVIS Device Control in Accessibility settings first.";
        }
        return text;
    }

    private static boolean isSupported(String action) {
        return switch (action) {
            case "back", "go back", "home", "go home", "scroll down", "down", "scroll forward", "scroll up", "up", "scroll backward" -> true;
            default -> false;
        };
    }
}
