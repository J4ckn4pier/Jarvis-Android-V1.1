#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
PROD_ROOT="android/app/src/main/java"

# A visible Backup & Sync control is allowed only when its persisted toggle has a
# non-Settings production runtime consumer. Preference-only controls are placebo UI.
if grep -F 'row("Backup & Sync"' "$SETTINGS" >/dev/null; then
  consumers=$(grep -R -l --include='*.java' 'backup_sync_enabled' "$PROD_ROOT" | grep -v '/SettingsActivity.java$' || true)
  if [ -z "$consumers" ]; then
    echo "Visible Backup & Sync persists backup_sync_enabled but production runtime never consumes it" >&2
    exit 1
  fi
fi

echo "Settings Backup & Sync runtime-truth contract passed"
# Final exact-head verification trigger after cleanup.
