#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
APK="$OUTPUT/app-debug.apk"
PACKAGE="com.jarvis.mobile"
ACTIVITY="$PACKAGE/com.jarvis.mobile.MainActivity"

# adb joins arguments into a remote shell command. Wrap the extra value in literal double-quotes
# so the device-side shell strips only those transport quotes and delivers the exact command text.
start_test_command() {
  command="$1"
  adb shell am start -W -n "$ACTIVITY" --es jarvis_test_command "\"$command\""
}

# Fixed APK-sprint regression: natural conversational lead-in must still execute the settings request.
adb shell am force-stop "$PACKAGE" || true
adb logcat -c
start_test_command "I'm good. Can you do me a favor and open settings, please?" \
  | tee "$OUTPUT/apk-sprint-settings-command-launch.txt"
grep -q 'Status: ok' "$OUTPUT/apk-sprint-settings-command-launch.txt"

SETTINGS_OPENED=0
for attempt in $(seq 1 30); do
  adb shell dumpsys activity activities > "$OUTPUT/apk-sprint-settings-activity.txt" || true
  adb logcat -d > "$OUTPUT/apk-sprint-settings-logcat.txt" || true
  if grep -Eq "JARVIS_RUNTIME_INPUT utterance=I'm good\. Can you do me a favor and open settings, please\?$" "$OUTPUT/apk-sprint-settings-logcat.txt" \
      && grep -Eq 'topResumedActivity=.*com\.jarvis\.mobile/\.SettingsActivity' "$OUTPUT/apk-sprint-settings-activity.txt"; then
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

# The visible destination itself must be the canonical user Settings surface, not the old
# provider/developer panel. Capture top/middle/lower regions and prove every required user group
# exists somewhere in the scrollable compiled screen.
adb shell uiautomator dump /sdcard/jarvis-apk-sprint-settings.xml >/dev/null
adb pull /sdcard/jarvis-apk-sprint-settings.xml "$OUTPUT/jarvis-apk-sprint-settings.xml" >/dev/null
adb exec-out screencap -p > "$OUTPUT/jarvis-apk-sprint-settings-command.png"

adb shell input swipe 540 1700 540 650 350 || true
sleep 1
adb shell uiautomator dump /sdcard/jarvis-apk-sprint-settings-middle.xml >/dev/null
adb pull /sdcard/jarvis-apk-sprint-settings-middle.xml "$OUTPUT/jarvis-apk-sprint-settings-middle.xml" >/dev/null
adb exec-out screencap -p > "$OUTPUT/jarvis-apk-sprint-settings-middle.png"

adb shell input swipe 540 1700 540 520 350 || true
sleep 1
adb shell uiautomator dump /sdcard/jarvis-apk-sprint-settings-lower.xml >/dev/null
adb pull /sdcard/jarvis-apk-sprint-settings-lower.xml "$OUTPUT/jarvis-apk-sprint-settings-lower.xml" >/dev/null
adb exec-out screencap -p > "$OUTPUT/jarvis-apk-sprint-settings-lower.png"

cat "$OUTPUT/jarvis-apk-sprint-settings.xml" \
    "$OUTPUT/jarvis-apk-sprint-settings-middle.xml" \
    "$OUTPUT/jarvis-apk-sprint-settings-lower.xml" \
    > "$OUTPUT/jarvis-apk-sprint-settings-all.xml"

grep -q 'SETTINGS' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"
grep -q 'Voice' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"
grep -q 'Wake Word' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"
grep -q 'Voice Model' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"
grep -q 'Language' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"
grep -q 'App Permissions' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"
grep -q 'AI Providers' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"
grep -q 'Backup &amp; Sync' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"
grep -q 'Profile' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"
grep -q 'Default Apps' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"
grep -q 'Personality' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"
grep -q 'Widgets &amp; Lock Screen' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"
! grep -Eiq 'PREFRONTAL CORTEX|API key|research endpoint|127\.0\.0\.1' "$OUTPUT/jarvis-apk-sprint-settings-all.xml"

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

# Generic installed-app routing must traverse the live APK path and resolve one exact visible
# launcher label. The shared contract suite separately supplies duplicate-label fakes and requires
# AndroidAppActions to refuse ambiguous packages instead of picking whichever package appears first.
adb shell am force-stop "$PACKAGE" || true
adb logcat -c
start_test_command 'open JARVIS' | tee "$OUTPUT/apk-sprint-open-app-launch.txt"
grep -q 'Status: ok' "$OUTPUT/apk-sprint-open-app-launch.txt"
APP_OPENED=0
for attempt in $(seq 1 20); do
  adb logcat -d > "$OUTPUT/apk-sprint-open-app-logcat.txt" || true
  adb shell dumpsys activity activities > "$OUTPUT/apk-sprint-open-app-activity.txt" || true
  if grep -Eq 'JARVIS_RUNTIME_INPUT utterance=open JARVIS$' "$OUTPUT/apk-sprint-open-app-logcat.txt" \
      && grep -Eiq 'JARVIS_COMMAND_RESULT Opened JARVIS\.$' "$OUTPUT/apk-sprint-open-app-logcat.txt" \
      && grep -Eq 'topResumedActivity=.*com\.jarvis\.mobile/\.MainActivity' "$OUTPUT/apk-sprint-open-app-activity.txt"; then
    APP_OPENED=1
    break
  fi
  sleep 1
done
if [ "$APP_OPENED" -ne 1 ]; then
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_COMMAND_RESULT' "$OUTPUT/apk-sprint-open-app-logcat.txt" || true
  cat "$OUTPUT/apk-sprint-open-app-activity.txt" || true
fi
test "$APP_OPENED" -eq 1

# The ordinary phrase "phone app" must use Android's typed ACTION_DIAL adapter, not guess a
# launcher label named Phone. On the Android 16 image this should visibly hand off to a dialer.
adb shell am force-stop "$PACKAGE" || true
adb logcat -c
start_test_command 'phone app' | tee "$OUTPUT/apk-sprint-dialer-launch.txt"
grep -q 'Status: ok' "$OUTPUT/apk-sprint-dialer-launch.txt"
DIALER_OPENED=0
for attempt in $(seq 1 20); do
  adb logcat -d > "$OUTPUT/apk-sprint-dialer-logcat.txt" || true
  adb shell dumpsys activity activities > "$OUTPUT/apk-sprint-dialer-activity.txt" || true
  if grep -Eq 'JARVIS_RUNTIME_INPUT utterance=phone app$' "$OUTPUT/apk-sprint-dialer-logcat.txt" \
      && grep -q 'JARVIS_COMMAND_RESULT Dialer opened.' "$OUTPUT/apk-sprint-dialer-logcat.txt" \
      && grep -Eq 'topResumedActivity=' "$OUTPUT/apk-sprint-dialer-activity.txt" \
      && ! grep -Eq 'topResumedActivity=.*com\.jarvis\.mobile/' "$OUTPUT/apk-sprint-dialer-activity.txt"; then
    DIALER_OPENED=1
    break
  fi
  sleep 1
done
if [ "$DIALER_OPENED" -ne 1 ]; then
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_COMMAND_RESULT' "$OUTPUT/apk-sprint-dialer-logcat.txt" || true
  grep -E 'topResumedActivity=' "$OUTPUT/apk-sprint-dialer-activity.txt" || true
fi
test "$DIALER_OPENED" -eq 1
adb exec-out screencap -p > "$OUTPUT/jarvis-apk-sprint-dialer.png"

# Truthfulness regression: when media volume is already at zero, "volume down" must not claim
# that Android lowered it. Modern Android exposes this through the media_session shell service.
adb shell cmd media_session volume --stream 3 --set 0 > "$OUTPUT/apk-sprint-volume-before.txt"
adb shell am force-stop "$PACKAGE" || true
adb logcat -c
start_test_command 'volume down' | tee "$OUTPUT/apk-sprint-volume-launch.txt"
grep -q 'Status: ok' "$OUTPUT/apk-sprint-volume-launch.txt"
VOLUME_TRUTHFUL=0
for attempt in $(seq 1 20); do
  adb logcat -d > "$OUTPUT/apk-sprint-volume-logcat.txt" || true
  adb shell cmd media_session volume --stream 3 --get > "$OUTPUT/apk-sprint-volume-after.txt" || true
  if grep -Eq 'JARVIS_RUNTIME_INPUT utterance=volume down$' "$OUTPUT/apk-sprint-volume-logcat.txt"; then
    if grep -q 'JARVIS_COMMAND_RESULT Volume lowered.' "$OUTPUT/apk-sprint-volume-logcat.txt"; then
      VOLUME_TRUTHFUL=0
    else
      VOLUME_TRUTHFUL=1
    fi
    break
  fi
  sleep 1
done
if [ "$VOLUME_TRUTHFUL" -ne 1 ]; then
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_COMMAND_RESULT' "$OUTPUT/apk-sprint-volume-logcat.txt" || true
  cat "$OUTPUT/apk-sprint-volume-before.txt" "$OUTPUT/apk-sprint-volume-after.txt" || true
fi
test "$VOLUME_TRUTHFUL" -eq 1
