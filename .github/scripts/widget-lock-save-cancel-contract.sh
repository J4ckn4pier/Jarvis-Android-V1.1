#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"

# Widgets & Lock Screen is a staged dialog: changing the checkbox must not mutate
# the production runtime preference until SAVE is explicitly pressed.
grep -q 'boolean\[\] stagedLock={lock}' "$SETTINGS"
grep -q 'if(which==0)stagedLock\[0\]=checked' "$SETTINGS"
grep -q 'setPositiveButton("SAVE"' "$SETTINGS"
grep -q 'putBoolean("lock_screen_assistant_enabled",stagedLock\[0\])' "$SETTINGS"
grep -q 'setNegativeButton("CANCEL",null)' "$SETTINGS"

# Guard against the regression where the checkbox listener itself persists runtime state.
! grep -q 'if(which==0)preferences.edit().putBoolean("lock_screen_assistant_enabled",checked).apply()' "$SETTINGS"
