#!/usr/bin/env bash
set -euo pipefail
FILE="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
METHOD=$(sed -n '/private void showWidgetLockSettings()/,/private void requestQuickAccessWidget()/p' "$FILE")

# Lock-screen preference must be staged in the dialog and persisted only on SAVE.
grep -q 'final boolean\[\] pendingLock' <<<"$METHOD"
grep -q 'pendingLock\[0\]=checked' <<<"$METHOD"
grep -q 'setPositiveButton("SAVE"' <<<"$METHOD"
grep -q 'putBoolean("lock_screen_assistant_enabled",pendingLock\[0\])' <<<"$METHOD"
grep -q 'setNegativeButton("CANCEL",null)' <<<"$METHOD"

# The multi-choice listener itself must not persist the preference.
LISTENER=$(sed -n '/setMultiChoiceItems/,/setPositiveButton/p' <<<"$METHOD")
if grep -q 'putBoolean("lock_screen_assistant_enabled"' <<<"$LISTENER"; then
  echo "lock-screen setting persists before SAVE" >&2
  exit 1
fi

echo "Widgets & Lock Screen SAVE/CANCEL contract passed"
