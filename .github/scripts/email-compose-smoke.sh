#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
ACTIVITY="$PACKAGE/com.jarvis.mobile.MainActivity"

# Keep JARVIS foreground before invoking the debug-only receiver so Android permits the
# production compose adapter to launch its ACTION_SENDTO review surface.
adb shell am start -W -n "$ACTIVITY" >/dev/null
adb logcat -c
adb shell am broadcast --receiver-foreground -a com.jarvis.mobile.DEBUG_TEST_EMAIL -p "$PACKAGE" \
  | tee "$OUTPUT/emulator-email-broadcast.txt"

EMAIL_PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-email-logcat.txt"
  if grep -q 'JARVIS_EMAIL_ACTION_RESULT.*Email draft ready for person+tag@example.com' "$OUTPUT/emulator-email-logcat.txt" \
      && grep -q 'JARVIS_EMAIL_CAPTURE.*encoded=person%2Btag%40example.com' "$OUTPUT/emulator-email-logcat.txt" \
      && grep -q 'JARVIS_EMAIL_CAPTURE.*decoded=person+tag@example.com' "$OUTPUT/emulator-email-logcat.txt" \
      && grep -q 'JARVIS_EMAIL_CAPTURE.*subject=Subject & details' "$OUTPUT/emulator-email-logcat.txt" \
      && grep -q 'JARVIS_EMAIL_CAPTURE.*body=Body line one' "$OUTPUT/emulator-email-logcat.txt"; then
    EMAIL_PASSED=1
    break
  fi
  sleep 1
done

if [ "$EMAIL_PASSED" -ne 1 ]; then
  echo '--- JARVIS email compose trace ---'
  grep -E 'JARVIS_EMAIL_ACTION_RESULT|JARVIS_EMAIL_CAPTURE|ActivityTaskManager|Background activity|SecurityException' "$OUTPUT/emulator-email-logcat.txt" || true
  cat "$OUTPUT/emulator-email-broadcast.txt" || true
fi

test "$EMAIL_PASSED" -eq 1
