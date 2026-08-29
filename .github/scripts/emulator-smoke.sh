#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
APK="$OUTPUT/app-debug.apk"
PACKAGE="com.jarvis.mobile"
ACTIVITY="$PACKAGE/com.jarvis.mobile.MainActivity"

adb install -r "$APK" | tee "$OUTPUT/emulator-install.txt"
grep -q '^Success$' "$OUTPUT/emulator-install.txt"

adb logcat -c
adb shell am start -W -n "$ACTIVITY" --ez jarvis_self_test true | tee "$OUTPUT/emulator-self-test-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-self-test-launch.txt"
SELF_TEST_PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-logcat.txt"
  if grep -q 'JARVIS_SELF_TEST_PASS' "$OUTPUT/emulator-logcat.txt"; then SELF_TEST_PASSED=1; break; fi
  if grep -q 'JARVIS_SELF_TEST_FAIL' "$OUTPUT/emulator-logcat.txt"; then grep -A 30 'JARVIS_SELF_TEST_FAIL' "$OUTPUT/emulator-logcat.txt"; exit 1; fi
  sleep 1
done
test "$SELF_TEST_PASSED" -eq 1
adb shell pidof "$PACKAGE"
adb shell uiautomator dump /sdcard/jarvis-self-test-ui.xml
adb pull /sdcard/jarvis-self-test-ui.xml "$OUTPUT/jarvis-self-test-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-self-test.png"

adb shell am force-stop "$PACKAGE"
adb logcat -c
adb shell am start -W -n "$ACTIVITY" --es jarvis_test_command "'help me!!!'" | tee "$OUTPUT/emulator-command-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-command-launch.txt"
COMMAND_PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-command-logcat.txt"
  if grep -q 'JARVIS_COMMAND_RESULT.*You can speak naturally' "$OUTPUT/emulator-command-logcat.txt" && grep -q 'JARVIS_COMMAND_RESULT.*call contacts' "$OUTPUT/emulator-command-logcat.txt"; then COMMAND_PASSED=1; break; fi
  sleep 1
done
test "$COMMAND_PASSED" -eq 1
adb shell uiautomator dump /sdcard/jarvis-command-ui.xml
adb pull /sdcard/jarvis-command-ui.xml "$OUTPUT/jarvis-command-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-command.png"

adb shell am force-stop "$PACKAGE"
adb logcat -c
adb shell am start -W -n "$ACTIVITY" --es jarvis_test_command "'how are you'" | tee "$OUTPUT/emulator-shared-brain-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-shared-brain-launch.txt"
SHARED_BRAIN_PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-shared-brain-logcat.txt"
  if grep -q 'JARVIS_SHARED_BRAIN_ACTIVE' "$OUTPUT/emulator-shared-brain-logcat.txt" && grep -q 'JARVIS_COMMAND_RESULT' "$OUTPUT/emulator-shared-brain-logcat.txt" && ! grep -Eiq 'JARVIS_COMMAND_RESULT.*(reliable interpretation|no framework)' "$OUTPUT/emulator-shared-brain-logcat.txt"; then
    SHARED_BRAIN_PASSED=1
    break
  fi
  sleep 1
done
test "$SHARED_BRAIN_PASSED" -eq 1

adb shell am force-stop "$PACKAGE"
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO
adb shell pm grant "$PACKAGE" android.permission.READ_CONTACTS
adb shell pm grant "$PACKAGE" android.permission.CALL_PHONE
adb shell pm grant "$PACKAGE" android.permission.CAMERA
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am start -W -n "$ACTIVITY" | tee "$OUTPUT/emulator-normal-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-normal-launch.txt"
HOME_PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-home-logcat.txt"
  if grep -q 'JARVIS_HOME_READY Original HUD Welcome Sir' "$OUTPUT/emulator-home-logcat.txt"; then HOME_PASSED=1; break; fi
  sleep 1
done
test "$HOME_PASSED" -eq 1
adb shell pidof "$PACKAGE"
adb shell uiautomator dump /sdcard/jarvis-home-ui.xml
adb pull /sdcard/jarvis-home-ui.xml "$OUTPUT/jarvis-home-ui.xml"
! grep -q 'PRIVATE ANDROID V1.1' "$OUTPUT/jarvis-home-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-home.png"

adb logcat -c
adb shell cmd role add-role-holder android.app.role.ASSISTANT "$PACKAGE"
adb shell cmd role get-role-holders android.app.role.ASSISTANT | tee "$OUTPUT/emulator-assistant-role.txt" | grep -q "^$PACKAGE$"
sleep 3
adb shell settings get secure assistant | tee "$OUTPUT/emulator-secure-assistant.txt" || true
adb shell settings get secure voice_interaction_service | tee "$OUTPUT/emulator-secure-voice-interaction-service.txt" || true
adb shell settings get secure voice_recognition_service | tee "$OUTPUT/emulator-secure-voice-recognition-service.txt" || true
adb shell dumpsys voiceinteraction | tee "$OUTPUT/emulator-voiceinteraction-dumpsys.txt" || true
adb shell dumpsys package "$PACKAGE" | tee "$OUTPUT/emulator-package-dumpsys.txt" || true
adb logcat -d > "$OUTPUT/emulator-assistant-preinvoke-logcat.txt"
grep -q 'JARVIS_VOICE_SERVICE_READY' "$OUTPUT/emulator-assistant-preinvoke-logcat.txt"

adb shell input keyevent KEYCODE_HOME
sleep 1
ASSISTANT_PASSED=0
adb logcat -c
adb shell input keyevent KEYCODE_ASSIST | tee "$OUTPUT/emulator-assistant-key-assist.txt"
for attempt in $(seq 1 10); do
  adb logcat -d > "$OUTPUT/emulator-assistant-key-assist-logcat.txt"
  if grep -q 'JARVIS_ASSISTANT_READY' "$OUTPUT/emulator-assistant-key-assist-logcat.txt"; then ASSISTANT_PASSED=1; break; fi
  sleep 1
done
if [ "$ASSISTANT_PASSED" -eq 0 ]; then
  adb logcat -c
  adb shell input keyevent KEYCODE_VOICE_ASSIST | tee "$OUTPUT/emulator-assistant-key-voice-assist.txt"
  for attempt in $(seq 1 10); do
    adb logcat -d > "$OUTPUT/emulator-assistant-key-voice-assist-logcat.txt"
    if grep -q 'JARVIS_ASSISTANT_READY' "$OUTPUT/emulator-assistant-key-voice-assist-logcat.txt"; then ASSISTANT_PASSED=1; break; fi
    sleep 1
  done
fi
adb logcat -d > "$OUTPUT/emulator-assistant-logcat.txt"
grep -E 'JARVIS_VOICE_SERVICE_READY|JARVIS_SESSION_SERVICE_NEW_SESSION|JARVIS_ASSISTANT_READY|VoiceInteraction|voiceinteraction|JarvisVoice|RecognitionService|SecurityException|FATAL EXCEPTION' "$OUTPUT/emulator-assistant-logcat.txt" || true
adb shell settings get secure assistant | tee "$OUTPUT/emulator-secure-assistant-post.txt" || true
adb shell settings get secure voice_interaction_service | tee "$OUTPUT/emulator-secure-voice-interaction-service-post.txt" || true
adb shell dumpsys voiceinteraction | tee "$OUTPUT/emulator-voiceinteraction-dumpsys-post.txt" || true
test "$ASSISTANT_PASSED" -eq 1
adb shell pidof "$PACKAGE"
adb shell uiautomator dump /sdcard/jarvis-assistant-ui.xml
adb pull /sdcard/jarvis-assistant-ui.xml "$OUTPUT/jarvis-assistant-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-assistant.png"
