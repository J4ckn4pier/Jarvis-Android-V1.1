#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"

test -f "$SETTINGS"

grep -q 'private void showWidgetLockSettings()' "$SETTINGS"
grep -q 'lock_screen_assistant_enabled' "$SETTINGS"

# Dialog-style runtime settings must stage edits and persist only from an explicit SAVE action.
if grep 'setMultiChoiceItems' "$SETTINGS" | grep -q 'putBoolean("lock_screen_assistant_enabled",checked)'; then
  echo "Widgets & Lock Screen persists runtime state immediately instead of honoring SAVE/CANCEL" >&2
  exit 1
fi

grep 'showWidgetLockSettings' "$SETTINGS" | grep -q 'setPositiveButton("SAVE"'
grep 'showWidgetLockSettings' "$SETTINGS" | grep -q 'putBoolean("lock_screen_assistant_enabled"'
grep 'showWidgetLockSettings' "$SETTINGS" | grep -q 'setNegativeButton("CANCEL",null)'

echo "Widgets & Lock Screen SAVE/CANCEL contract satisfied"
