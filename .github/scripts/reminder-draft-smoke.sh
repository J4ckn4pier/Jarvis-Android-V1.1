#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
ACTIVITY="$PACKAGE/com.jarvis.mobile.MainActivity"

start_test_command() {
  command="$1"
  adb shell am start -W -n "$ACTIVITY" --es jarvis_test_command "\"$command\""
}

# Compiled-APK proof that a reminder remains local and opens an Android confirmation surface.
# The RED phase intentionally expects a debug capture target that is not present yet; production
# reminder code is not changed until this test proves the exact missing handoff evidence.
adb shell am force-stop "$PACKAGE" || true
adb logcat -c
start_test_command 'remind me to call Mom' | tee "$OUTPUT/reminder-draft-launch.txt"
grep -q 'Status: ok' "$OUTPUT/reminder-draft-launch.txt"

REMINDER_PROVEN=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/reminder-draft-logcat.txt" || true
  if grep -Eq 'JARVIS_RUNTIME_INPUT utterance=remind me to call Mom$' "$OUTPUT/reminder-draft-logcat.txt" \
      && grep -Fq 'JARVIS_REMINDER_CAPTURE title=remind me to call Mom' "$OUTPUT/reminder-draft-logcat.txt"; then
    REMINDER_PROVEN=1
    break
  fi
  sleep 1
done

if [ "$REMINDER_PROVEN" -ne 1 ]; then
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_COMMAND_RESULT|JARVIS_REMINDER_CAPTURE' "$OUTPUT/reminder-draft-logcat.txt" || true
fi

test "$REMINDER_PROVEN" -eq 1
