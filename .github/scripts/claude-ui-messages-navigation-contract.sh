#!/bin/sh
set -eu

ROUTER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"
SCREEN="android/app/src/main/java/com/jarvis/mobile/ui/MessagesActivity.java"
MANIFEST="android/app/src/main/AndroidManifest.xml"

[ -f "$SCREEN" ] || { echo "missing Claude-authored MessagesActivity" >&2; exit 1; }
grep -q 'ACTION_MESSAGES = "messages"' "$ROUTER" || { echo "messages action not advertised" >&2; exit 1; }
grep -q 'new Intent(activity, MessagesActivity.class)' "$ROUTER" || { echo "messages action not routed to production screen" >&2; exit 1; }
grep -q 'case ACTION_MESSAGES:' "$ROUTER" || { echo "messages action not marked supported" >&2; exit 1; }
grep -q '\.ui.MessagesActivity' "$MANIFEST" || { echo "MessagesActivity not registered" >&2; exit 1; }
grep -q 'UiSection.MESSAGES' "$SCREEN" || { echo "MessagesActivity is not backed by the real UI backend" >&2; exit 1; }

echo "Claude messages navigation contract GREEN"
