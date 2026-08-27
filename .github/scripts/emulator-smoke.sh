#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
APK="$OUTPUT/app-debug.apk"

adb install -r "$APK" | tee "$OUTPUT/emulator-install.txt"
grep -q '^Success$' "$OUTPUT/emulator-install.txt"

adb logcat -c
adb shell am start -W \
  -n com.itsmylab.jarvis/com.jarvis.mobile.MainActivity \
  --ez jarvis_self_test true \
  | tee "$OUTPUT/emulator-self-test-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-self-test-launch.txt"

SELF_TEST_PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-logcat.txt"
  if grep -q 'JARVIS_SELF_TEST_PASS' "$OUTPUT/emulator-logcat.txt"; then
    SELF_TEST_PASSED=1
    break
  fi
  if grep -q 'JARVIS_SELF_TEST_FAIL' "$OUTPUT/emulator-logcat.txt"; then
    grep -A 30 'JARVIS_SELF_TEST_FAIL' "$OUTPUT/emulator-logcat.txt"
    exit 1
  fi
  sleep 1
done
test "$SELF_TEST_PASSED" -eq 1
adb shell pidof com.itsmylab.jarvis

adb shell uiautomator dump /sdcard/jarvis-self-test-ui.xml
adb pull /sdcard/jarvis-self-test-ui.xml "$OUTPUT/jarvis-self-test-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-self-test.png"

adb shell am force-stop com.itsmylab.jarvis
adb shell am start -W \
  -n com.itsmylab.jarvis/com.jarvis.mobile.MainActivity \
  --es jarvis_test_command "'help me!!!'" \
  | tee "$OUTPUT/emulator-command-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-command-launch.txt"
sleep 2
adb shell uiautomator dump /sdcard/jarvis-command-ui.xml
adb pull /sdcard/jarvis-command-ui.xml "$OUTPUT/jarvis-command-ui.xml"
grep -q 'You can speak naturally' "$OUTPUT/jarvis-command-ui.xml"
grep -q 'call contacts' "$OUTPUT/jarvis-command-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-command.png"

adb shell am force-stop com.itsmylab.jarvis
adb shell pm grant com.itsmylab.jarvis android.permission.RECORD_AUDIO
adb shell pm grant com.itsmylab.jarvis android.permission.READ_CONTACTS
adb shell pm grant com.itsmylab.jarvis android.permission.CALL_PHONE
adb shell pm grant com.itsmylab.jarvis android.permission.CAMERA
adb shell pm grant com.itsmylab.jarvis android.permission.POST_NOTIFICATIONS
adb shell am start -W \
  -n com.itsmylab.jarvis/com.jarvis.mobile.MainActivity \
  | tee "$OUTPUT/emulator-normal-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-normal-launch.txt"
sleep 2
adb shell pidof com.itsmylab.jarvis
adb shell uiautomator dump /sdcard/jarvis-home-ui.xml
adb pull /sdcard/jarvis-home-ui.xml "$OUTPUT/jarvis-home-ui.xml"
grep -q 'JARVIS Mark III interface' "$OUTPUT/jarvis-home-ui.xml"
grep -q 'Welcome Sir!' "$OUTPUT/jarvis-home-ui.xml"
! grep -q 'PRIVATE ANDROID V1.1' "$OUTPUT/jarvis-home-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-home.png"

adb shell cmd role add-role-holder android.app.role.ASSISTANT com.itsmylab.jarvis
adb shell cmd role get-role-holders android.app.role.ASSISTANT \
  | grep -q '^com.itsmylab.jarvis$'
adb shell am start -W -a android.intent.action.ASSIST \
  | tee "$OUTPUT/emulator-assistant-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-assistant-launch.txt"
sleep 2
adb shell uiautomator dump /sdcard/jarvis-assistant-ui.xml
adb pull /sdcard/jarvis-assistant-ui.xml "$OUTPUT/jarvis-assistant-ui.xml"
grep -q 'OPEN FULL JARVIS' "$OUTPUT/jarvis-assistant-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-assistant.png"
