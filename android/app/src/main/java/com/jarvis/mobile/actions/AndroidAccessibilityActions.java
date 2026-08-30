package com.jarvis.mobile.actions;

import com.jarvis.mobile.hands.JarvisAccessibilityService;

import java.util.Locale;

/** Typed Android accessibility actions. UI mutation methods remain approval-gated by their tool specs. */
public final class AndroidAccessibilityActions {
    public String navigate(String action) {
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!isSupported(normalized)) return "Unsupported device navigation action: " + normalized;
        if (!JarvisAccessibilityService.isConnected()) {
            return "Enable JARVIS Device Control in Accessibility settings first.";
        }
        boolean completed = switch (normalized) {
            case "back", "go back" -> JarvisAccessibilityService.back();
            case "home", "go home" -> JarvisAccessibilityService.home();
            case "scroll down", "down", "scroll forward" -> JarvisAccessibilityService.scrollForward();
            case "scroll up", "up", "scroll backward" -> JarvisAccessibilityService.scrollBackward();
            default -> false;
        };
        if (!completed) return "JARVIS could not complete that device navigation action on the current screen.";
        return switch (normalized) {
            case "back", "go back" -> "Went back.";
            case "home", "go home" -> "Went home.";
            case "scroll down", "down", "scroll forward" -> "Scrolled down.";
            default -> "Scrolled up.";
        };
    }

    public String readScreen() {
        if (!JarvisAccessibilityService.isConnected()) {
            return "Enable JARVIS Device Control in Accessibility settings first.";
        }
        String text = JarvisAccessibilityService.screenText();
        if (text == null || text.isBlank()) {
            return "No readable text is visible on the current screen.";
        }
        return text;
    }

    public String click(String target) {
        if (target == null || target.isBlank()) return "Tell me the exact visible control to tap.";
        if (!JarvisAccessibilityService.isConnected()) {
            return "Enable JARVIS Device Control in Accessibility settings first.";
        }
        String clean = target.trim().replaceAll("\\s+", " ");
        if (!JarvisAccessibilityService.clickText(clean)) {
            return "JARVIS could not find one unique clickable control exactly matching '" + clean + "' on the current screen.";
        }
        return "Tapped the uniquely matching '" + clean + "' control.";
    }

    public String type(String text) {
        if (text == null) return "Tell me what text to enter.";
        if (!JarvisAccessibilityService.isConnected()) {
            return "Enable JARVIS Device Control in Accessibility settings first.";
        }
        if (!JarvisAccessibilityService.typeText(text)) {
            return "JARVIS could not find one unique editable field to type into on the current screen.";
        }
        return "Entered the approved text into the uniquely selected field.";
    }

    private static boolean isSupported(String action) {
        return switch (action) {
            case "back", "go back", "home", "go home", "scroll down", "down", "scroll forward", "scroll up", "up", "scroll backward" -> true;
            default -> false;
        };
    }
}
