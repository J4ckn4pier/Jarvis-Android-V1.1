#!/bin/sh
set -eu

DEV="android/app/src/main/java/com/jarvis/mobile/DeveloperSettingsActivity.java"
USER="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"

[ -f "$DEV" ] || { echo "RED: missing developer settings" >&2; exit 1; }
[ -f "$USER" ] || { echo "RED: missing canonical user settings" >&2; exit 1; }

require() {
  needle="$1"
  file="$2"
  grep -F "$needle" "$file" >/dev/null || { echo "Missing remote-settings contract token '$needle' in $file" >&2; exit 1; }
}

require 'RemoteGoalStateStore' "$DEV"
require 'Remote JARVIS endpoint' "$DEV"
require 'Connection token' "$DEV"
require 'SAVE REMOTE CONNECTION' "$DEV"
require 'CLEAR REMOTE CONNECTION' "$DEV"
require 'saveConnection' "$DEV"
require 'clearConnection' "$DEV"
require 'TYPE_TEXT_VARIATION_PASSWORD' "$DEV"

# Clearing the persisted connection must immediately clear the visible endpoint too.
# Otherwise Developer Options claims a remote endpoint is still configured after disconnect.
require 'remoteEndpoint.setText("");' "$DEV"

# Normal user settings stays product-facing and must not expose raw remote URLs/tokens.
if grep -Eiq 'remote jarvis endpoint|connection token|bearer token|save remote connection' "$USER"; then
  echo "Remote developer connection controls leaked into normal Settings" >&2
  exit 1
fi

# A saved token must never be placed back into an EditText or shown in a Toast/status string.
if grep -Eq 'setText\([^)]*\.token\(\)|Toast[^;]*\.token\(\)|note\([^)]*\.token\(\)' "$DEV"; then
  echo "Saved remote token is being echoed into developer UI" >&2
  exit 1
fi

echo "Remote developer settings contract GREEN"