#!/bin/sh
set -eu

ROUTER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"

for action in media_previous media_play_pause media_next; do
  grep -q "\"${action}\"" "$ROUTER" || {
    echo "Claude UI media contract missing action: ${action}" >&2
    exit 1
  }
done

grep -q 'AudioManager' "$ROUTER" || {
  echo "Claude UI media contract must route through Android AudioManager" >&2
  exit 1
}
grep -q 'KEYCODE_MEDIA_PREVIOUS' "$ROUTER" || exit 1
grep -q 'KEYCODE_MEDIA_PLAY_PAUSE' "$ROUTER" || exit 1
grep -q 'KEYCODE_MEDIA_NEXT' "$ROUTER" || exit 1

echo "Claude UI media control contract GREEN"
