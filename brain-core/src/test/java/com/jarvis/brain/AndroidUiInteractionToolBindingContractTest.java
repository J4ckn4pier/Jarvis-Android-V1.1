package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Consequential screen tap/type tools must never guess a target and must remain approval-gated. */
public final class AndroidUiInteractionToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String registry = Files.readString(Path.of("src/main/java/com/jarvis/brain/ToolRegistry.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        String actions = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidAccessibilityActions.java"));
        String service = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/hands/JarvisAccessibilityService.java"));

        check(registry.contains("spec(\"ui_click\", true"), "screen clicking must be consequential and approval-gated");
        check(registry.contains("spec(\"ui_type\", true"), "screen typing must be consequential and approval-gated");
        check(registry.contains("Set.of(\"target\")"), "clicking must require an explicit visible target");
        check(registry.contains("Set.of(\"text\")"), "typing must require explicit text");

        ToolRegistry runtimeRegistry = ToolRegistry.standard();
        ToolSpec clickSpec = runtimeRegistry.specs().stream().filter(spec -> "ui_click".equals(spec.name())).findFirst()
                .orElseThrow(() -> new AssertionError("ui_click runtime tool missing"));
        ToolSpec typeSpec = runtimeRegistry.specs().stream().filter(spec -> "ui_type".equals(spec.name())).findFirst()
                .orElseThrow(() -> new AssertionError("ui_type runtime tool missing"));
        check(clickSpec.consequential() && clickSpec.executionClass() == ToolExecutionClass.CONSEQUENTIAL,
                "ui_click must reach runtime as a consequential approval-gated tool");
        check(typeSpec.consequential() && typeSpec.executionClass() == ToolExecutionClass.CONSEQUENTIAL,
                "ui_type must reach runtime as a consequential approval-gated tool");
        check(clickSpec.requiredArguments().contains("target"), "ui_click runtime contract must require target");
        check(typeSpec.requiredArguments().contains("text"), "ui_type runtime contract must require text");

        check(service.contains("UniqueNamedTargetResolver.resolve"), "click targeting must use the shared exact fail-closed resolver");
        check(service.contains("clickableTargets.size()"), "click targeting must de-duplicate clickable ancestors before resolving");
        check(!service.contains("if (candidate.isClickable() && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK))"),
                "service must not click the first partial text match it encounters");
        check(service.contains("focusedEditables.size() == 1"), "typing must prefer exactly one focused editable field");
        check(service.contains("editableFields.size() == 1"), "typing may fall back only when exactly one editable field exists");
        check(!service.contains("if (node.isEditable()) return node;"), "typing must not fall back to the first editable field in the tree");

        check(actions.contains("public String click(String target)"), "typed Android adapter must expose click");
        check(actions.contains("public String type(String text)"), "typed Android adapter must expose typing");
        check(actions.contains("could not find one unique clickable control exactly matching"), "ambiguous/missing clicks must fail truthfully");
        check(actions.contains("could not find one unique editable field"), "ambiguous/missing typing targets must fail truthfully");

        check(factory.contains("register(registry, \"ui_click\", true"), "Android factory must preserve consequential click classification");
        check(factory.contains("register(registry, \"ui_type\", true"), "Android factory must preserve consequential type classification");
        check(factory.contains("args -> accessibility.click(args.get(\"target\"))"), "Android factory must bind click to typed accessibility actions");
        check(factory.contains("args -> accessibility.type(args.get(\"text\"))"), "Android factory must bind typing to typed accessibility actions");

        System.out.println("AndroidUiInteractionToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
