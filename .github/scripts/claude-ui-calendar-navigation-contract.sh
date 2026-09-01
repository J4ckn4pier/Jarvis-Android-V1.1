#!/bin/sh
set -eu

ROUTER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"
SCREEN="android/app/src/main/java/com/jarvis/mobile/ui/CalendarActivity.java"
MANIFEST="android/app/src/main/AndroidManifest.xml"

[ -f "$SCREEN" ] || { echo "missing Claude-authored CalendarActivity" >&2; exit 1; }
grep -q 'ACTION_CALENDAR = "calendar"' "$ROUTER" || { echo "calendar action not advertised" >&2; exit 1; }
grep -q 'new Intent(activity, CalendarActivity.class)' "$ROUTER" || { echo "calendar action not routed to production screen" >&2; exit 1; }
grep -q 'case ACTION_CALENDAR:' "$ROUTER" || { echo "calendar action not marked supported" >&2; exit 1; }
grep -q '\.ui.CalendarActivity' "$MANIFEST" || { echo "CalendarActivity not registered" >&2; exit 1; }
grep -q 'UiSection.CALENDAR' "$SCREEN" || { echo "CalendarActivity is not backed by the real UI backend" >&2; exit 1; }

echo "Claude calendar navigation contract GREEN"
