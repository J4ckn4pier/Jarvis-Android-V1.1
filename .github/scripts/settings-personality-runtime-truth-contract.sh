#!/usr/bin/env bash
set -euo pipefail

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
PROD="android/app/src/main/java"

# A visible Personality setting is only truthful when production code outside
# Settings actually consumes the saved preference.
if ! grep -R --include='*.java' --exclude='SettingsActivity.java' -q '"personality_label"' "$PROD"; then
  if grep -q 'row("Personality"' "$SETTINGS" || \
     grep -q 'showPersonalityPicker' "$SETTINGS" || \
     grep -q 'putString("personality_label"' "$SETTINGS"; then
    echo "Personality is visible/persisted but has no production runtime consumer" >&2
    exit 1
  fi
fi

echo "Settings Personality runtime-truth contract passed"
