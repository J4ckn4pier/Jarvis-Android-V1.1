#!/bin/sh
set -eu

ROUTER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"
SCREEN="android/app/src/main/java/com/jarvis/mobile/ui/DevicesActivity.java"
MANIFEST="android/app/src/main/AndroidManifest.xml"

[ -f "$SCREEN" ] || { echo "missing Claude-authored DevicesActivity" >&2; exit 1; }
grep -q 'ACTION_DEVICES = "devices"' "$ROUTER" || { echo "devices action not advertised" >&2; exit 1; }
grep -q 'new Intent(activity, DevicesActivity.class)' "$ROUTER" || { echo "devices action not routed to production screen" >&2; exit 1; }
grep -q 'case ACTION_DEVICES:' "$ROUTER" || { echo "devices action not marked supported" >&2; exit 1; }
grep -q '\.ui.DevicesActivity' "$MANIFEST" || { echo "DevicesActivity not registered" >&2; exit 1; }
grep -q 'ui\.devices()' "$SCREEN" || { echo "DevicesActivity is not backed by the real device backend" >&2; exit 1; }

echo "Claude devices navigation contract GREEN"
