#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
APK="$OUTPUT/app-debug.apk"
PACKAGE="com.jarvis.mobile"
ACTIVITY="$PACKAGE/com.jarvis.mobile.MainActivity"

# Android 16 can briefly return a null accessibility root immediately after an Activity reports
# ready. Device evidence should fail only if the tree never becomes available, not on one transient
# publication gap. Keep per-attempt evidence where possible for diagnosis.
dump_ui_retry() {
  REMOTE="$1"
  LOCAL="$2"
  UI_DUMP_READY=0
  for attempt in $(seq 1 15); do
    adb shell rm -f "$REMOTE" >/dev/null 2>&1 || true
    adb shell uiautomator dump "$REMOTE" >/dev/null 2>&1 || true
    if adb pull "$REMOTE" "$LOCAL.attempt-$attempt.xml" >/dev/null 2>&1 \
        && grep -q '<hierarchy' "$LOCAL.attempt-$attempt.xml"; then
      cp "$LOCAL.attempt-$attempt.xml" "$LOCAL"
      UI_DUMP_READY=1
      break
    fi
    sleep 1
  done
  if [ "$UI_DUMP_READY" -ne 1 ]; then
    adb shell dumpsys activity activities > "$LOCAL.activity.txt" || true
    echo "Android accessibility tree unavailable after retries: $REMOTE"
  fi
  test "$UI_DUMP_READY" -eq 1
}

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
dump_ui_retry /sdcard/jarvis-self-test-ui.xml "$OUTPUT/jarvis-self-test-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-self-test.png"

adb shell am force-stop "$PACKAGE"
adb logcat -c
adb shell am start -W -n "$ACTIVITY" --es jarvis_test_command '"help me!!!"' | tee "$OUTPUT/emulator-command-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-command-launch.txt"
COMMAND_PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-command-logcat.txt"
  if grep -q 'JARVIS_COMMAND_RESULT.*You can speak naturally' "$OUTPUT/emulator-command-logcat.txt" && grep -q 'JARVIS_COMMAND_RESULT.*call contacts' "$OUTPUT/emulator-command-logcat.txt"; then COMMAND_PASSED=1; break; fi
  sleep 1
done
if [ "$COMMAND_PASSED" -ne 1 ]; then
  echo '--- JARVIS command trace ---'
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_COMMAND_RESULT' "$OUTPUT/emulator-command-logcat.txt" || true
  echo '--- Activity launch evidence ---'
  cat "$OUTPUT/emulator-command-launch.txt" || true
fi
test "$COMMAND_PASSED" -eq 1
dump_ui_retry /sdcard/jarvis-command-ui.xml "$OUTPUT/jarvis-command-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-command.png"

adb shell am force-stop "$PACKAGE"
adb logcat -c
adb shell am start -W -n "$ACTIVITY" --es jarvis_test_command '"how are you"' | tee "$OUTPUT/emulator-shared-brain-launch.txt"
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

# Prove the full app exposes real consequential-decision controls on Android 16 and that
# cancelling through the UI clears the shared runtime decision without executing the message.
adb shell am force-stop "$PACKAGE"
adb logcat -c
adb shell am start -W -n "$ACTIVITY" --es jarvis_test_command '"Jarvis, text Mom I am on my way"' | tee "$OUTPUT/emulator-decision-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-decision-launch.txt"
DECISION_PENDING=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-decision-logcat.txt"
  if grep -q 'JARVIS_RUNTIME_OUTPUT state=AWAITING_APPROVAL' "$OUTPUT/emulator-decision-logcat.txt"; then DECISION_PENDING=1; break; fi
  sleep 1
done
if [ "$DECISION_PENDING" -ne 1 ]; then
  echo '--- JARVIS pending-decision trace ---'
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_SHARED_BRAIN_ACTIVE|JARVIS_COMMAND_RESULT' "$OUTPUT/emulator-decision-logcat.txt" || true
fi
test "$DECISION_PENDING" -eq 1

DECISION_CONTROLS_READY=0
for attempt in $(seq 1 15); do
  adb shell uiautomator dump /sdcard/jarvis-decision-ui.xml || true
  if adb pull /sdcard/jarvis-decision-ui.xml "$OUTPUT/jarvis-decision-ui-attempt-$attempt.xml"; then
    if grep -q 'content-desc="JARVIS APPROVE action"' "$OUTPUT/jarvis-decision-ui-attempt-$attempt.xml" \
        && grep -q 'content-desc="JARVIS CANCEL action"' "$OUTPUT/jarvis-decision-ui-attempt-$attempt.xml"; then
      cp "$OUTPUT/jarvis-decision-ui-attempt-$attempt.xml" "$OUTPUT/jarvis-decision-ui.xml"
      DECISION_CONTROLS_READY=1
      break
    fi
  fi
  sleep 1
done
if [ "$DECISION_CONTROLS_READY" -ne 1 ]; then
  adb shell dumpsys activity activities > "$OUTPUT/emulator-decision-activity.txt" || true
  if ! grep -q 'com.jarvis.mobile/.MainActivity' "$OUTPUT/emulator-decision-activity.txt"; then
    echo 'decision activity left foreground before controls could be inspected'
  else
    echo 'decision controls missing after runtime reached AWAITING_APPROVAL'
  fi
  exit 1
fi
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-decision-pending.png"
python3 - "$OUTPUT/jarvis-decision-ui.xml" > "$OUTPUT/jarvis-cancel-tap.txt" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.attrib.get('content-desc') == 'JARVIS CANCEL action':
        match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib['bounds'])
        if not match:
            raise SystemExit('CANCEL control has invalid bounds')
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
else:
    raise SystemExit('CANCEL control missing from UI tree')
PY
set -- $(cat "$OUTPUT/jarvis-cancel-tap.txt")
adb shell input tap "$1" "$2"
DECISION_CANCELLED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-decision-cancel-logcat.txt"
  if grep -q 'JARVIS_SHARED_BRAIN_ACTIVE.*state=IDLE' "$OUTPUT/emulator-decision-cancel-logcat.txt"; then DECISION_CANCELLED=1; break; fi
  sleep 1
done
if [ "$DECISION_CANCELLED" -ne 1 ]; then
  echo '--- JARVIS cancellation trace ---'
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_SHARED_BRAIN_ACTIVE|JARVIS_COMMAND_RESULT' "$OUTPUT/emulator-decision-cancel-logcat.txt" || true
fi
test "$DECISION_CANCELLED" -eq 1
dump_ui_retry /sdcard/jarvis-decision-cancelled-ui.xml "$OUTPUT/jarvis-decision-cancelled-ui.xml"
! grep -q 'content-desc="JARVIS APPROVE action"' "$OUTPUT/jarvis-decision-cancelled-ui.xml"
! grep -q 'content-desc="JARVIS CANCEL action"' "$OUTPUT/jarvis-decision-cancelled-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-decision-cancelled.png"

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
dump_ui_retry /sdcard/jarvis-home-ui.xml "$OUTPUT/jarvis-home-ui.xml"
! grep -q 'PRIVATE ANDROID V1.1' "$OUTPUT/jarvis-home-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-home.png"

# Prove the secondary user-facing screens actually start and render on Android 16.
adb shell am start -W -n "$PACKAGE/.CommandsActivity" | tee "$OUTPUT/emulator-help-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-help-launch.txt"
dump_ui_retry /sdcard/jarvis-help-ui.xml "$OUTPUT/jarvis-help-ui.xml"
grep -q 'JARVIS COMMANDS' "$OUTPUT/jarvis-help-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-help.png"

adb shell am start -W -n "$PACKAGE/.NotesActivity" | tee "$OUTPUT/emulator-notes-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-notes-launch.txt"
dump_ui_retry /sdcard/jarvis-notes-ui.xml "$OUTPUT/jarvis-notes-ui.xml"
grep -q 'ADD NOTE' "$OUTPUT/jarvis-notes-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-notes.png"

# Prove live-research configuration is reachable only from the installed advanced Developer Options UI.
adb shell am start -W -n "$PACKAGE/.DeveloperSettingsActivity" | tee "$OUTPUT/emulator-developer-settings-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-developer-settings-launch.txt"
dump_ui_retry /sdcard/jarvis-developer-settings-ui.xml "$OUTPUT/jarvis-developer-settings-ui.xml"
grep -q 'DEVELOPER OPTIONS' "$OUTPUT/jarvis-developer-settings-ui.xml"
grep -q 'Research endpoint' "$OUTPUT/jarvis-developer-settings-ui.xml"
grep -q 'SAVE RESEARCH ENDPOINT' "$OUTPUT/jarvis-developer-settings-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-developer-settings.png"

# Prove a zero-cost local research endpoint actually saves and survives an Activity/process restart.
RESEARCH_ENDPOINT='http://127.0.0.1:8765/research'
RESEARCH_CONTROL_READY=0
for attempt in $(seq 1 8); do
  dump_ui_retry /sdcard/jarvis-research-controls.xml "$OUTPUT/jarvis-research-controls.xml"
  if grep -q 'content-desc="JARVIS research endpoint"' "$OUTPUT/jarvis-research-controls.xml"; then
    RESEARCH_CONTROL_READY=1
    break
  fi
  adb shell input swipe 500 1500 500 300 250
  sleep 1
done
test "$RESEARCH_CONTROL_READY" -eq 1
python3 - "$OUTPUT/jarvis-research-controls.xml" 'JARVIS research endpoint' > "$OUTPUT/jarvis-research-endpoint-tap.txt" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.attrib.get('content-desc') == sys.argv[2]:
        m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib['bounds'])
        if not m: raise SystemExit('invalid research endpoint bounds')
        x1,y1,x2,y2 = map(int,m.groups())
        print((x1+x2)//2,(y1+y2)//2)
        break
else: raise SystemExit('research endpoint control missing')
PY
set -- $(cat "$OUTPUT/jarvis-research-endpoint-tap.txt")
adb shell input tap "$1" "$2"
adb shell input text "$RESEARCH_ENDPOINT"
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input swipe 500 1400 500 650 200 || true
dump_ui_retry /sdcard/jarvis-research-save-controls.xml "$OUTPUT/jarvis-research-save-controls.xml"
python3 - "$OUTPUT/jarvis-research-save-controls.xml" 'JARVIS save research endpoint' > "$OUTPUT/jarvis-research-save-tap.txt" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.attrib.get('content-desc') == sys.argv[2]:
        m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib['bounds'])
        if not m: raise SystemExit('invalid research save bounds')
        x1,y1,x2,y2 = map(int,m.groups())
        print((x1+x2)//2,(y1+y2)//2)
        break
else: raise SystemExit('research save control missing')
PY
set -- $(cat "$OUTPUT/jarvis-research-save-tap.txt")
adb shell input tap "$1" "$2"
sleep 1
dump_ui_retry /sdcard/jarvis-developer-settings-saved-ui.xml "$OUTPUT/jarvis-developer-settings-saved-ui.xml"
grep -q '127.0.0.1:8765/research' "$OUTPUT/jarvis-developer-settings-saved-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-developer-settings-saved.png"
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.DeveloperSettingsActivity" | tee "$OUTPUT/emulator-developer-settings-reopen-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-developer-settings-reopen-launch.txt"
for attempt in $(seq 1 8); do
  dump_ui_retry /sdcard/jarvis-developer-settings-reopened-ui.xml "$OUTPUT/jarvis-developer-settings-reopened-ui.xml"
  if grep -q '127.0.0.1:8765/research' "$OUTPUT/jarvis-developer-settings-reopened-ui.xml"; then break; fi
  adb shell input swipe 500 1500 500 300 250
  sleep 1
done
grep -q '127.0.0.1:8765/research' "$OUTPUT/jarvis-developer-settings-reopened-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-developer-settings-reopened.png"

# Prove Android owns the assistant selection and has bound JARVIS's VoiceInteractionService.
adb shell input keyevent KEYCODE_HOME
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
adb logcat -c
adb shell am broadcast -a com.jarvis.mobile.DEBUG_SHOW_ASSISTANT -p "$PACKAGE" | tee "$OUTPUT/emulator-assistant-debug-trigger.txt"
VOICE_SESSION_PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-assistant-logcat.txt"
  if grep -q 'JARVIS_DEBUG_SESSION_REQUEST_ACCEPTED' "$OUTPUT/emulator-assistant-logcat.txt" && grep -q 'JARVIS_SESSION_SERVICE_NEW_SESSION' "$OUTPUT/emulator-assistant-logcat.txt" && grep -q 'JARVIS_ASSISTANT_READY' "$OUTPUT/emulator-assistant-logcat.txt"; then
    VOICE_SESSION_PASSED=1
    break
  fi
  sleep 1
done
if [ "$VOICE_SESSION_PASSED" -ne 1 ]; then
  echo '--- JARVIS assistant invocation trace ---'
  grep -E 'JARVIS_VOICE_SERVICE_READY|JARVIS_DEBUG_SESSION|JARVIS_SESSION_SERVICE_NEW_SESSION|JARVIS_ASSISTANT_READY|VoiceInteraction|voiceinteraction|JarvisVoice|RecognitionService|SecurityException|FATAL EXCEPTION' "$OUTPUT/emulator-assistant-logcat.txt" || true
  cat "$OUTPUT/emulator-assistant-debug-trigger.txt" || true
fi
adb shell settings get secure assistant | tee "$OUTPUT/emulator-secure-assistant-post.txt" || true
adb shell settings get secure voice_interaction_service | tee "$OUTPUT/emulator-secure-voice-interaction-service-post.txt" || true
adb shell dumpsys voiceinteraction | tee "$OUTPUT/emulator-voiceinteraction-dumpsys-post.txt" || true
test "$VOICE_SESSION_PASSED" -eq 1
adb shell pidof "$PACKAGE"
dump_ui_retry /sdcard/jarvis-assistant-ui.xml "$OUTPUT/jarvis-assistant-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-assistant.png"
