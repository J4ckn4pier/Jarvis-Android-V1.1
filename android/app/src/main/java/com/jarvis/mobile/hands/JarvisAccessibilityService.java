package com.jarvis.mobile.hands;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.jarvis.brain.UniqueNamedTargetResolver;

import java.util.ArrayList;
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
        List<AccessibilityNodeInfo> clickableTargets = new ArrayList<>();
        List<UniqueNamedTargetResolver.Candidate> candidates = new ArrayList<>();
        for (AccessibilityNodeInfo match : matches) {
            AccessibilityNodeInfo clickable = clickableAncestor(match);
            if (clickable == null) continue;
            int targetIndex = indexOfNode(clickableTargets, clickable);
            if (targetIndex < 0) {
                clickableTargets.add(clickable);
                targetIndex = clickableTargets.size() - 1;
            }
            String targetId = Integer.toString(targetIndex);
            addLabelCandidate(candidates, match.getText(), targetId);
            addLabelCandidate(candidates, match.getContentDescription(), targetId);
            addLabelCandidate(candidates, match.getHintText(), targetId);
        }
        String resolved = UniqueNamedTargetResolver.resolve(text, candidates).orElse(null);
        if (resolved == null) return false;
        try {
            int index = Integer.parseInt(resolved);
            return index >= 0 && index < clickableTargets.size()
                    && clickableTargets.get(index).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static boolean typeText(String text) {
        AccessibilityNodeInfo field = uniqueEditable(root());
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

    private static AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo candidate = node;
        while (candidate != null) {
            if (candidate.isClickable()) return candidate;
            candidate = candidate.getParent();
        }
        return null;
    }

    private static int indexOfNode(List<AccessibilityNodeInfo> nodes, AccessibilityNodeInfo candidate) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).equals(candidate)) return i;
        }
        return -1;
    }

    private static void addLabelCandidate(List<UniqueNamedTargetResolver.Candidate> candidates, CharSequence label, String targetId) {
        if (label == null) return;
        String clean = label.toString().trim().replaceAll("\\s+", " ");
        if (!clean.isEmpty()) candidates.add(new UniqueNamedTargetResolver.Candidate(clean, targetId));
    }

    private static AccessibilityNodeInfo uniqueEditable(AccessibilityNodeInfo node) {
        List<AccessibilityNodeInfo> editableFields = new ArrayList<>();
        collectEditables(node, editableFields);
        List<AccessibilityNodeInfo> focusedEditables = new ArrayList<>();
        for (AccessibilityNodeInfo field : editableFields) {
            if (field.isFocused() || field.isAccessibilityFocused()) focusedEditables.add(field);
        }
        if (focusedEditables.size() == 1) return focusedEditables.get(0);
        if (!focusedEditables.isEmpty()) return null;
        return editableFields.size() == 1 ? editableFields.get(0) : null;
    }

    private static void collectEditables(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> output) {
        if (node == null) return;
        if (node.isEditable() && indexOfNode(output, node) < 0) output.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collectEditables(node.getChild(i), output);
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
