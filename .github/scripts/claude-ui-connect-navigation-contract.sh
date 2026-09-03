#!/bin/sh
set -eu

ROUTER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"
SCREEN="android/app/src/main/java/com/jarvis/mobile/ui/ConnectActivity.java"
MANIFEST="android/app/src/main/AndroidManifest.xml"
HUB="android/app/src/main/java/com/jarvis/mobile/ui/JarvisHubActivity.java"

[ -f "$SCREEN" ] || { echo "missing Claude-staged ConnectActivity integration" >&2; exit 1; }
grep -q 'ACTION_CONNECT = "connect"' "$ROUTER" || { echo "connect action not advertised" >&2; exit 1; }
grep -q 'new Intent(activity, ConnectActivity.class)' "$ROUTER" || { echo "connect action not routed to production screen" >&2; exit 1; }
grep -q 'case ACTION_CONNECT:' "$ROUTER" || { echo "connect action not marked supported" >&2; exit 1; }
grep -q '\.ui.ConnectActivity' "$MANIFEST" || { echo "ConnectActivity not registered" >&2; exit 1; }
grep -q 'ui\.connections()' "$SCREEN" || { echo "ConnectActivity is not backed by the real connection registry" >&2; exit 1; }
grep -q 'open(ConnectActivity.class)' "$HUB" || { echo "ConnectActivity not exposed from JARVIS Hub" >&2; exit 1; }

echo "Claude connect navigation contract GREEN"
