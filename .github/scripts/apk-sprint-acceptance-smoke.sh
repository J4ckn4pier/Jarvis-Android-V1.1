#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
APK="$OUTPUT/app-debug.apk"
PACKAGE="com.jarvis.mobile"
ACTIVITY="$PACKAGE/com.jarvis.mobile.MainActivity"

# Fixed APK-sprint regression: natural conversational lead-in must still execute the settings request.
adb shell am force-stop "$PACKAGE" || true
adb logcat -c
adb shell am start -W -n "$ACTIVITY" --es jarvis_test_command '"I'"'"'m good. Can you do me a favor and open settings, please?'"'"'"' \
  | tee "$OUTPUT/apk-sprint-settings-command-launch.txt"
grep -q 'Status: ok' "$OUTPUT/apk-sprint-settings-command-launch.txt"

SETTINGS_OPENED=0
for attempt in $(seq 1 30); do
  adb shell dumpsys activity activities > "$OUTPUT/apk-sprint-settings-activity.txt" || true
  adb logcat -d > "$OUTPUT/apk-sprint-settings-logcat.txt" || true
  if grep -Eq 'topResumedActivity=.*com\.jarvis\.mobile/\.SettingsActivity' "$OUTPUT/apk-sprint-settings-activity.txt"; then
    sleep 1
    adb shell dumpsys activity activities > "$OUTPUT/apk-sprint-settings-activity-stable.txt" || true
    if grep -Eq 'topResumedActivity=.*com\.jarvis\.mobile/\.SettingsActivity' "$OUTPUT/apk-sprint-settings-activity-stable.txt"; then
      SETTINGS_OPENED=1
      break
    fi
  fi
  sleep 1
done
if [ "$SETTINGS_OPENED" -ne 1 ]; then
  echo 'Exact natural settings regression did not leave SettingsActivity visibly open.'
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_COMMAND_RESULT' "$OUTPUT/apk-sprint-settings-logcat.txt" || true
  cat "$OUTPUT/apk-sprint-settings-activity.txt" || true
fi
test "$SETTINGS_OPENED" -eq 1
adb exec-out screencap -p > "$OUTPUT/jarvis-apk-sprint-settings-command.png"

# Shipped UI must not expose developer/demo scenario controls.
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$ACTIVITY" | tee "$OUTPUT/apk-sprint-home-launch.txt"
grep -q 'Status: ok' "$OUTPUT/apk-sprint-home-launch.txt"
UI_READY=0
for attempt in $(seq 1 15); do
  adb shell rm -f /sdcard/jarvis-apk-sprint-home.xml >/dev/null 2>&1 || true
  adb shell uiautomator dump /sdcard/jarvis-apk-sprint-home.xml >/dev/null 2>&1 || true
  if adb pull /sdcard/jarvis-apk-sprint-home.xml "$OUTPUT/jarvis-apk-sprint-home.xml" >/dev/null 2>&1 \
      && grep -q '<hierarchy' "$OUTPUT/jarvis-apk-sprint-home.xml"; then
    UI_READY=1
    break
  fi
  sleep 1
done
test "$UI_READY" -eq 1
! grep -Eiq 'Scripted Scenarios|Scenario Picker|Demo Mode|Developer Sandbox|Sandbox' "$OUTPUT/jarvis-apk-sprint-home.xml"
grep -Eiq 'JARVIS|Welcome Sir|Speak to JARVIS' "$OUTPUT/jarvis-apk-sprint-home.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-apk-sprint-home.png"
