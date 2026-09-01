#!/bin/sh
set -eu

ROUTER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"
SCREEN="android/app/src/main/java/com/jarvis/mobile/ui/MusicActivity.java"
MANIFEST="android/app/src/main/AndroidManifest.xml"

[ -f "$SCREEN" ] || { echo "missing Claude-staged MusicActivity integration" >&2; exit 1; }
grep -q 'ACTION_MUSIC = "music"' "$ROUTER" || { echo "music action not advertised" >&2; exit 1; }
grep -q 'new Intent(activity, MusicActivity.class)' "$ROUTER" || { echo "music action not routed to production screen" >&2; exit 1; }
grep -q 'case ACTION_MUSIC:' "$ROUTER" || { echo "music action not marked supported" >&2; exit 1; }
grep -q '\.ui.MusicActivity' "$MANIFEST" || { echo "MusicActivity not registered" >&2; exit 1; }
grep -q 'ui\.music()' "$SCREEN" || { echo "MusicActivity is not backed by the real music backend" >&2; exit 1; }

echo "Claude music navigation contract GREEN"
