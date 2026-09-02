#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
KEY="backup_sync_enabled"

test -f "$SETTINGS"

if grep -q 'Backup & Sync' "$SETTINGS"; then
  consumers=$(grep -R -l --include='*.java' --include='*.kt' "$KEY" android/app/src/main/java | grep -v '/SettingsActivity.java$' || true)
  if [ -z "$consumers" ]; then
    echo "Backup & Sync is visible but backup_sync_enabled has no production runtime consumer" >&2
    exit 1
  fi
fi

if ! grep -R -l --include='*.java' --include='*.kt' "$KEY" android/app/src/main/java | grep -v '/SettingsActivity.java$' >/dev/null 2>&1; then
  ! grep -q 'Backup & Sync' "$SETTINGS"
  ! grep -q 'showBackupSyncSettings' "$SETTINGS"
  ! grep -q 'backupSummary' "$SETTINGS"
fi

echo "Backup & Sync runtime-truth contract satisfied"
