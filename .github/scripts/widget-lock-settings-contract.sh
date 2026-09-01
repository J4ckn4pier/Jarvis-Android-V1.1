#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
SESSION="android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"

require_absent() {
  pattern="$1"
  file="$2"
  message="$3"
  if grep -Fq "$pattern" "$file"; then
    echo "$message" >&2
    exit 1
  fi
}

# Settings no longer exposes a private JARVIS lock-screen permission. Android owns that policy.
require_absent 'lock_screen_assistant_enabled' "$SETTINGS" \
  'Settings must not persist the retired lock_screen_assistant_enabled preference.'

# Production must agree with the visible Settings surface. A stale historical false value must not
# silently hide the assistant after the user has no remaining control that can repair it.
require_absent 'lock_screen_assistant_enabled' "$SESSION" \
  'Voice runtime still consumes retired lock_screen_assistant_enabled state.'
require_absent 'lockScreenAssistantAllowed()' "$SESSION" \
  'Voice runtime still gates sessions on the retired private lock-screen setting.'

echo "Widget/lock-screen settings contract GREEN"
