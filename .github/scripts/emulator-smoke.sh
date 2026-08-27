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
adb logcat -c
adb shell am start -W \
  -n com.itsmylab.jarvis/com.jarvis.mobile.MainActivity \
  --es jarvis_test_command "'help me!!!'" \
  | tee "$OUTPUT/emulator-command-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-command-launch.txt"
COMMAND_PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-command-logcat.txt"
  if grep -q 'JARVIS_COMMAND_RESULT.*You can speak naturally' "$OUTPUT/emulator-command-logcat.txt" && \
     grep -q 'JARVIS_COMMAND_RESULT.*call contacts' "$OUTPUT/emulator-command-logcat.txt"; then
    COMMAND_PASSED=1
    break
  fi
  sleep 1
done
test "$COMMAND_PASSED" -eq 1
adb shell uiautomator dump /sdcard/jarvis-command-ui.xml
adb pull /sdcard/jarvis-command-ui.xml "$OUTPUT/jarvis-command-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-command.png"

adb shell am force-stop com.itsmylab.jarvis
adb shell pm grant com.itsmylab.jarvis android.permission.RECORD_AUDIO
adb shell pm grant com.itsmylab.jarvis android.permission.READ_CONTACTS
adb shell pm grant com.itsmylab.jarvis android.permission.CALL_PHONE
adb shell pm grant com.itsmylab.jarvis android.permission.CAMERA
adb shell pm grant com.itsmylab.jarvis android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am start -W \
  -n com.itsmylab.jarvis/com.jarvis.mobile.MainActivity \
  | tee "$OUTPUT/emulator-normal-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-normal-launch.txt"
HOME_PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-home-logcat.txt"
  if grep -q 'JARVIS_HOME_READY Mark III Welcome Sir' "$OUTPUT/emulator-home-logcat.txt"; then
    HOME_PASSED=1
    break
  fi
  sleep 1
done
test "$HOME_PASSED" -eq 1
adb shell pidof com.itsmylab.jarvis
adb shell uiautomator dump /sdcard/jarvis-home-ui.xml
adb pull /sdcard/jarvis-home-ui.xml "$OUTPUT/jarvis-home-ui.xml"
! grep -q 'PRIVATE ANDROID V1.1' "$OUTPUT/jarvis-home-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-home.png"

# Assign JARVIS as the Android assistant and capture the framework state before
# attempting invocation. These diagnostics distinguish "role holder" from an
# actually parsed/bound VoiceInteractionService.
adb logcat -c
adb shell cmd role add-role-holder android.app.role.ASSISTANT com.itsmylab.jarvis
adb shell cmd role get-role-holders android.app.role.ASSISTANT \
  | tee "$OUTPUT/emulator-assistant-role.txt" \
  | grep -q '^com.itsmylab.jarvis$'
sleep 3
adb shell settings get secure assistant \
  | tee "$OUTPUT/emulator-secure-assistant.txt" || true
adb shell settings get secure voice_interaction_service \
  | tee "$OUTPUT/emulator-secure-voice-interaction-service.txt" || true
adb shell settings get secure voice_recognition_service \
  | tee "$OUTPUT/emulator-secure-voice-recognition-service.txt" || true
adb shell dumpsys voiceinteraction \
  | tee "$OUTPUT/emulator-voiceinteraction-dumpsys.txt" || true
adb shell dumpsys package com.itsmylab.jarvis \
  | tee "$OUTPUT/emulator-package-dumpsys.txt" || true
adb logcat -d > "$OUTPUT/emulator-assistant-preinvoke-logcat.txt"

VOICE_SERVICE_READY=0
if grep -q 'JARVIS_VOICE_SERVICE_READY' "$OUTPUT/emulator-assistant-preinvoke-logcat.txt"; then
  VOICE_SERVICE_READY=1
fi
echo "JARVIS voice service ready before invocation: $VOICE_SERVICE_READY"

adb shell am force-stop com.itsmylab.jarvis
adb shell input keyevent KEYCODE_HOME
adb logcat -c
adb shell input keyevent KEYCODE_VOICE_ASSIST \
  | tee "$OUTPUT/emulator-assistant-launch.txt"
ASSISTANT_PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-assistant-logcat.txt"
  if grep -q 'JARVIS_ASSISTANT_READY' "$OUTPUT/emulator-assistant-logcat.txt"; then
    ASSISTANT_PASSED=1
    break
  fi
  sleep 1
done

# Always capture post-invocation framework state before applying the gate.
adb shell settings get secure assistant \
  | tee "$OUTPUT/emulator-secure-assistant-post.txt" || true
adb shell settings get secure voice_interaction_service \
  | tee "$OUTPUT/emulator-secure-voice-interaction-service-post.txt" || true
adb shell dumpsys voiceinteraction \
  | tee "$OUTPUT/emulator-voiceinteraction-dumpsys-post.txt" || true
adb logcat -d > "$OUTPUT/emulator-assistant-logcat.txt"
grep -E 'VoiceInteraction|voiceinteraction|JARVIS_ASSISTANT_TEST|JarvisVoice|RecognitionService|SecurityException|FATAL EXCEPTION' \
  "$OUTPUT/emulator-assistant-logcat.txt" || true

test "$ASSISTANT_PASSED" -eq 1
adb shell pidof com.itsmylab.jarvis
adb shell uiautomator dump /sdcard/jarvis-assistant-ui.xml
adb pull /sdcard/jarvis-assistant-ui.xml "$OUTPUT/jarvis-assistant-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-assistant.png"
