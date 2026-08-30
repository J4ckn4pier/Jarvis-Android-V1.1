package com.jarvis.mobile.hands;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JarvisAccessibilityService extends AccessibilityService {
    private static volatile JarvisAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isConnected() {
        return instance != null;
    }

    public static boolean back() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public static boolean home() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public static boolean scrollForward() {
        return performFirstAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    public static boolean scrollBackward() {
        return performFirstAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }

    private static boolean performFirstAction(int action) {
        AccessibilityNodeInfo root = root();
        return performRecursive(root, action);
    }

    private static boolean performRecursive(AccessibilityNodeInfo node, int action) {
        if (node == null) return false;
        if (node.isScrollable() && node.performAction(action)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (performRecursive(node.getChild(i), action)) return true;
        }
        return false;
    }

    public static boolean clickText(String text) {
        AccessibilityNodeInfo root = root();
        if (root == null || text == null || text.trim().isEmpty()) return false;
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(text.trim());
        for (AccessibilityNodeInfo match : matches) {
            AccessibilityNodeInfo candidate = match;
            while (candidate != null) {
                if (candidate.isClickable() && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
                candidate = candidate.getParent();
            }
        }
        return false;
    }

    public static boolean typeText(String text) {
        AccessibilityNodeInfo field = findEditable(root());
        if (field == null) return false;
        Bundle arguments = new Bundle();
        arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text == null ? "" : text);
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }

    public static String screenText() {
        AccessibilityNodeInfo root = root();
        if (root == null) return "";
        Set<String> values = new LinkedHashSet<>();
        collectText(root, values);
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(" • ");
            if (result.length() + value.length() > 2200) break;
            result.append(value);
        }
        return result.toString().trim();
    }

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && (node.isFocused() || node.isAccessibilityFocused())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findEditable(node.getChild(i));
            if (result != null) return result;
        }
        if (node.isEditable()) return node;
        return null;
    }

    private static void collectText(AccessibilityNodeInfo node, Set<String> output) {
        if (node == null) return;
        add(output, node.getText());
        add(output, node.getContentDescription());
        add(output, node.getHintText());
        for (int i = 0; i < node.getChildCount(); i++) collectText(node.getChild(i), output);
    }

    private static void add(Set<String> output, CharSequence value) {
        if (value == null) return;
        String clean = value.toString().trim().replaceAll("\\s+", " ");
        if (!clean.isEmpty()) output.add(clean);
    }

    private static AccessibilityNodeInfo root() {
        return instance == null ? null : instance.getRootInActiveWindow();
    }
}
