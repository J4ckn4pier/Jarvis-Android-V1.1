#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
SESSION="android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"

require() {
  pattern="$1"
  file="$2"
  message="$3"
  if ! grep -Fq "$pattern" "$file"; then
    echo "$message" >&2
    exit 1
  fi
}

# The production session intentionally supports an app-level lock-screen allowance. If runtime
# consumes that preference, Settings must expose the same state so users can inspect and repair it.
require 'getBoolean("lock_screen_assistant_enabled", true)' "$SESSION" \
  'Voice runtime no longer consumes the lock-screen assistant preference; update this contract.'
require 'lockScreenAssistantAllowed()' "$SESSION" \
  'Voice runtime no longer gates locked-device sessions through the configured allowance.'
require '"lock_screen_assistant_enabled"' "$SETTINGS" \
  'Settings does not expose the lock-screen preference consumed by production runtime.'
require 'putBoolean("lock_screen_assistant_enabled",checked)' "$SETTINGS" \
  'Settings does not persist the lock-screen allowance selected by the user.'
require 'setNegativeButton("CANCEL",null)' "$SETTINGS" \
  'Widgets & Lock Screen must provide a non-mutating CANCEL path.'

echo "Widget/lock-screen settings contract GREEN"
