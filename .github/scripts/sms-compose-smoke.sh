#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
ACTIVITY="$PACKAGE/com.jarvis.mobile.MainActivity"

start_test_command() {
  command="$1"
  adb shell am start -W -n "$ACTIVITY" --es jarvis_test_command "\"$command\""
}

# Compiled-APK proof for SMS compose. The debug build owns a capture-only smsto: target so CI can
# inspect the exact Android handoff without transmitting a real message. Production behavior remains
# the normal ACTION_SENDTO compose flow and still requires the user to send from the messaging app.
adb shell am force-stop "$PACKAGE" || true
adb logcat -c
start_test_command "Text 5550100 saying I'm on my way" | tee "$OUTPUT/sms-compose-launch.txt"
grep -q 'Status: ok' "$OUTPUT/sms-compose-launch.txt"

SMS_PROVEN=0
for attempt in $(seq 1 20); do
  adb logcat -d > "$OUTPUT/sms-compose-logcat.txt" || true
  if grep -Eq "JARVIS_RUNTIME_INPUT utterance=Text 5550100 saying I'm on my way$" "$OUTPUT/sms-compose-logcat.txt" \
      && grep -Eq "JARVIS_SMS_CAPTURE number=(5550100|555%200100|5550100) body=I'm on my way$" "$OUTPUT/sms-compose-logcat.txt"; then
    SMS_PROVEN=1
    break
  fi
  sleep 1
done

if [ "$SMS_PROVEN" -ne 1 ]; then
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_COMMAND_RESULT|JARVIS_SMS_CAPTURE' "$OUTPUT/sms-compose-logcat.txt" || true
fi

test "$SMS_PROVEN" -eq 1
