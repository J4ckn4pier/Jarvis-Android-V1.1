#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"

test -f "$SETTINGS"

if grep -q 'private String permissionSummary(){return "Microphone, contacts, calendar, notifications and screen control";}' "$SETTINGS"; then
  echo "App Permissions summary is static and does not reflect Android runtime access" >&2
  exit 1
fi

grep -q 'Manifest.permission.RECORD_AUDIO' "$SETTINGS"
grep -q 'Manifest.permission.READ_CONTACTS' "$SETTINGS"
grep -q 'Manifest.permission.READ_CALENDAR' "$SETTINGS"
grep -q 'Settings.Secure.ENABLED_NOTIFICATION_LISTENERS' "$SETTINGS"
grep -q 'Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES' "$SETTINGS"
grep -q 'checkSelfPermission' "$SETTINGS"
grep -q 'Needs setup:' "$SETTINGS"

echo "App Permissions runtime-truth contract satisfied"
