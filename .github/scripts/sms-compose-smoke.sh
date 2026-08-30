#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
ACTIVITY="$PACKAGE/com.jarvis.mobile.MainActivity"
MESSAGING_PACKAGE="com.android.messaging"

start_test_command() {
  command="$1"
  adb shell am start -W -n "$ACTIVITY" --es jarvis_test_command "\"$command\""
}

# The stock emulator Messaging app targets an old Android version and may show a one-time system
# compatibility notice on first launch. Clear only that harmless notice before the real proof so it
# cannot hide the compose fields. Never interact with a send control.
adb shell monkey -p "$MESSAGING_PACKAGE" 1 >/dev/null 2>&1 || true
for attempt in $(seq 1 10); do
  adb shell uiautomator dump /sdcard/jarvis-sms-preflight.xml >/dev/null 2>&1 || true
  if adb pull /sdcard/jarvis-sms-preflight.xml "$OUTPUT/sms-compose-preflight.xml" >/dev/null 2>&1; then
    if python3 - "$OUTPUT/sms-compose-preflight.xml" > "$OUTPUT/sms-compose-preflight-ok-tap.txt" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
text = ' '.join((node.attrib.get('text') or '') for node in root.iter('node'))
if 'built for an older version of Android' not in text:
    raise SystemExit(1)
for node in root.iter('node'):
    if node.attrib.get('resource-id') == 'android:id/button1' and node.attrib.get('text') == 'OK':
        match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
        if not match:
            raise SystemExit('Compatibility OK control has invalid bounds')
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        raise SystemExit(0)
raise SystemExit('Compatibility notice found but safe OK control missing')
PY
    then
      set -- $(cat "$OUTPUT/sms-compose-preflight-ok-tap.txt")
      adb shell input tap "$1" "$2"
      sleep 1
      break
    fi
  fi
  sleep 1
done
adb shell am force-stop "$MESSAGING_PACKAGE" || true

# Compiled-APK proof for the real SMS approval + native compose handoff. CI must never transmit a
# message: it approves only JARVIS's existing consequential-action gate, then inspects Android's
# actual Messaging compose screen and verifies both recipient and body without tapping Send.
adb shell am force-stop "$PACKAGE" || true
adb logcat -c
start_test_command "Text 5550100 saying I'm on my way" | tee "$OUTPUT/sms-compose-launch.txt"
grep -q 'Status: ok' "$OUTPUT/sms-compose-launch.txt"

APPROVAL_READY=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/sms-compose-logcat.txt" || true
  if grep -Fq "JARVIS_RUNTIME_INPUT utterance=Text 5550100 saying I'm on my way" "$OUTPUT/sms-compose-logcat.txt" \
      && grep -q 'JARVIS_RUNTIME_OUTPUT state=AWAITING_APPROVAL' "$OUTPUT/sms-compose-logcat.txt"; then
    APPROVAL_READY=1
    break
  fi
  sleep 1
done
if [ "$APPROVAL_READY" -ne 1 ]; then
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_COMMAND_RESULT|JARVIS_SMS_CAPTURE' "$OUTPUT/sms-compose-logcat.txt" || true
fi
test "$APPROVAL_READY" -eq 1

adb shell uiautomator dump /sdcard/jarvis-sms-approval.xml
adb pull /sdcard/jarvis-sms-approval.xml "$OUTPUT/sms-compose-approval.xml"
python3 - "$OUTPUT/sms-compose-approval.xml" > "$OUTPUT/sms-compose-approve-tap.txt" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.attrib.get('content-desc') == 'JARVIS APPROVE action':
        match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib['bounds'])
        if not match:
            raise SystemExit('SMS APPROVE control has invalid bounds')
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
else:
    raise SystemExit('SMS APPROVE control missing from UI tree')
PY
set -- $(cat "$OUTPUT/sms-compose-approve-tap.txt")
adb shell input tap "$1" "$2"

SMS_PROVEN=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/sms-compose-logcat.txt" || true
  adb shell uiautomator dump /sdcard/jarvis-sms-compose-ui.xml >/dev/null 2>&1 || true
  if adb pull /sdcard/jarvis-sms-compose-ui.xml "$OUTPUT/sms-compose-ui.xml" >/dev/null 2>&1; then
    if grep -q 'act=android.intent.action.SENDTO' "$OUTPUT/sms-compose-logcat.txt" \
        && grep -q 'dat=smsto:' "$OUTPUT/sms-compose-logcat.txt" \
        && python3 - "$OUTPUT/sms-compose-ui.xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
texts = [(node.attrib.get('text') or '') for node in root.iter('node')]
joined = ' '.join(texts)
if 'built for an older version of Android' in joined:
    raise SystemExit(1)
if not any("I'm on my way" in value for value in texts):
    raise SystemExit(1)
if not any('5550100' in re.sub(r'\D', '', value) for value in texts):
    raise SystemExit(1)
PY
    then
      SMS_PROVEN=1
      break
    fi
  fi
  sleep 1
done

if [ "$SMS_PROVEN" -ne 1 ]; then
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_COMMAND_RESULT|JARVIS_SMS_CAPTURE|android.intent.action.SENDTO' "$OUTPUT/sms-compose-logcat.txt" || true
  cat "$OUTPUT/sms-compose-ui.xml" 2>/dev/null || true
fi

test "$SMS_PROVEN" -eq 1
