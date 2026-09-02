#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
MAIN_JAVA="android/app/src/main/java"

# A visible Backup & Sync setting is only truthful if production code outside
# SettingsActivity actually consumes the preference and changes runtime behavior.
if grep -q 'Backup & Sync' "$SETTINGS"; then
  if ! grep -R --include='*.java' -n 'backup_sync_enabled' "$MAIN_JAVA" \
      | grep -v 'SettingsActivity.java' >/dev/null; then
    echo "FAIL: Backup & Sync is visible but backup_sync_enabled has no production runtime consumer." >&2
    exit 1
  fi
fi

echo "PASS: Backup & Sync surface is runtime-truthful."
