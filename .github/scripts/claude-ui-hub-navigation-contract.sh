#!/bin/sh
set -eu

HUB="android/app/src/main/java/com/jarvis/mobile/ui/JarvisHubActivity.java"

for activity in RoutinesActivity CalendarActivity MessagesActivity DevicesActivity BrowserActivity SkillsActivity OverlaysActivity; do
  if ! grep -q "open(${activity}.class)" "$HUB"; then
    echo "Missing truthful JARVIS Hub navigation entry for ${activity}" >&2
    exit 1
  fi
done

echo "Claude UI hub navigation contract: PASS"
