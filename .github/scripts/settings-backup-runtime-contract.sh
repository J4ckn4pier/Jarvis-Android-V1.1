#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
KEY="backup_sync_enabled"

test -f "$SETTINGS"

# A visible production setting may exist only if production runtime outside Settings consumes it.
if grep -q 'Backup & Sync' "$SETTINGS"; then
  consumers=$(grep -R -l --include='*.java' --include='*.kt' "$KEY" android/app/src/main/java | grep -v '/SettingsActivity.java$' || true)
  if [ -z "$consumers" ]; then
    echo "Backup & Sync is visible but backup_sync_enabled has no production runtime consumer" >&2
    exit 1
  fi
fi

# If the product has no sync runtime yet, do not expose a placebo toggle.
if ! grep -R -l --include='*.java' --include='*.kt' "$KEY" android/app/src/main/java | grep -v '/SettingsActivity.java$' >/dev/null 2>&1; then
  ! grep -q 'Backup & Sync' "$SETTINGS"
  ! grep -q 'showBackupSyncSettings' "$SETTINGS"
  ! grep -q 'backupSummary' "$SETTINGS"
fi

echo "Backup & Sync runtime-truth contract satisfied"
