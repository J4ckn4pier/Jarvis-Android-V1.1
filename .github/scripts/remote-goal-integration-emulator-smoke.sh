#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
ACTIVITY="$PACKAGE/com.jarvis.mobile.MainActivity"
PORT=18081
LOG="/tmp/jarvis-remote-integration.log"
MODE="/tmp/jarvis-remote-integration-mode"
PID="/tmp/jarvis-remote-integration.pid"

cleanup() {
  adb reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ ! -f "$PID" ] || kill "$(cat "$PID")" >/dev/null 2>&1 || true
}
trap cleanup EXIT
: > "$LOG"
echo submit > "$MODE"

python3 - "$PORT" "$LOG" "$MODE" <<'PY' &
import json, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
port=int(sys.argv[1]); log_path=sys.argv[2]; mode_path=sys.argv[3]

def mode(): return open(mode_path, encoding='utf-8').read().strip()
def write_mode(value): open(mode_path,'w',encoding='utf-8').write(value+'\n')
class H(BaseHTTPRequestHandler):
    def log_message(self,*args): return
    def body(self):
        n=int(self.headers.get('Content-Length','0')); return self.rfile.read(n).decode() if n else ''
    def send_json(self,payload,status=200):
        raw=json.dumps(payload).encode(); self.send_response(status); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(raw))); self.end_headers(); self.wfile.write(raw)
    def record(self, body=''):
        with open(log_path,'a',encoding='utf-8') as f: f.write(f'{self.command} {self.path} auth={self.headers.get("Authorization","")} body={body}\n')
    def do_POST(self):
        body=self.body(); self.record(body)
        if self.path == '/v1/goals':
            self.send_json({'project_id':'project-submit','session_id':'primary','state':'active','goal':json.loads(body)['goal'],'provider_details_exposed':False}); return
        if self.path == '/v1/projects/project-approval/approvals/approval-emulator':
            data=json.loads(body)
            if data.get('approved') is not True: self.send_json({'error':'expected approval'},422); return
            write_mode('approval-completed')
            self.send_json({'project_id':'project-approval','approval_id':'approval-emulator','approved':True,'response':data.get('response')}); return
        if self.path == '/v1/projects/project-cancel/cancel':
            write_mode('cancelled')
            self.send_json({'project_id':'project-cancel','state':'cancelled'}); return
        self.send_json({'error':'not found'},404)
    def do_GET(self):
        self.record(); m=mode()
        if self.path == '/v1/projects/project-expired/events?after_event_id=expired-emulator':
            self.send_json({'detail':'Recovery cursor is no longer available'},410); return
        if self.path.startswith('/v1/projects/') and self.path.endswith('/events'):
            project=self.path.split('/')[3]
            self.send_json({'project_id':project,'events':[{'event_id':'evt-'+project,'project_id':project,'kind':'task_progress','task_id':'task-1','timestamp':'2026-08-30T21:00:00Z'}],'next_event_id':'evt-'+project,'has_more':False}); return
        if '/events?' in self.path:
            project=self.path.split('/')[3]
            self.send_json({'project_id':project,'events':[],'next_event_id':self.path.split('after_event_id=',1)[1],'has_more':False}); return
        if self.path == '/v1/projects/project-submit':
            self.send_json({'project_id':'project-submit','session_id':'primary','goal':'remote submission','state':'active','task_count':2,'task_states':{'completed':1},'pending_approvals':[],'last_progress_at':'2026-08-30T21:00:00Z','provider_details_exposed':False}); return
        if self.path == '/v1/projects/project-approval':
            completed=m=='approval-completed'
            self.send_json({'project_id':'project-approval','session_id':'primary','goal':'approval proof','state':'completed' if completed else 'active','task_count':1,'task_states':{'completed':1 if completed else 0},'pending_approvals':[] if completed else [{'approval_id':'approval-emulator','task_id':'task-approval'}],'last_progress_at':'2026-08-30T21:00:00Z','provider_details_exposed':False}); return
        if self.path == '/v1/projects/project-approval/result':
            self.send_json({'project_id':'project-approval','state':'completed','result':'APPROVAL_FINAL_RESULT_VISIBLE','provider_details_exposed':False}); return
        if self.path == '/v1/projects/project-cancel':
            self.send_json({'project_id':'project-cancel','session_id':'primary','goal':'cancel proof','state':'active','task_count':2,'task_states':{'completed':1},'pending_approvals':[],'last_progress_at':'2026-08-30T21:00:00Z','provider_details_exposed':False}); return
        if self.path == '/v1/projects/project-expired':
            self.send_json({'project_id':'project-expired','session_id':'primary','goal':'expired cursor proof','state':'active','task_count':3,'task_states':{'completed':2},'pending_approvals':[],'last_progress_at':'2026-08-30T21:05:00Z','provider_details_exposed':False}); return
        self.send_json({'error':'not found'},404)
ThreadingHTTPServer(('127.0.0.1',port),H).serve_forever()
PY
echo $! > "$PID"
sleep 1
adb reverse "tcp:$PORT" "tcp:$PORT"

seed() {
  project="$1"
  event_id="${2:-}"
  args=""
  [ -z "$project" ] || args="$args --es project_id $project"
  [ -z "$event_id" ] || args="$args --es event_id $event_id"
  # shellcheck disable=SC2086
  adb shell am broadcast -a com.jarvis.mobile.DEBUG_SEED_REMOTE_GOAL -n com.jarvis.mobile/.remote.RemoteGoalStateTestReceiver --es base_url "http://127.0.0.1:$PORT" --es token emulator-secret $args | tee "$OUTPUT/remote-integration-seed.txt"
  grep -q 'Broadcast completed: result=1' "$OUTPUT/remote-integration-seed.txt"
}
start_command() {
  command="$1"
  adb shell am start -W -n "$ACTIVITY" --es jarvis_test_command "\"$command\""
}
dump_ui() {
  name="$1"
  adb shell uiautomator dump "/sdcard/$name.xml" >/dev/null 2>&1 || true
  adb pull "/sdcard/$name.xml" "$OUTPUT/$name.xml" >/dev/null
}
tap_desc() {
  file="$1"; desc="$2"
  coords=$(python3 - "$file" "$desc" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    if n.attrib.get('content-desc') == sys.argv[2]:
        b=list(map(int,re.findall(r'\d+',n.attrib['bounds']))); print((b[0]+b[2])//2,(b[1]+b[3])//2); raise SystemExit
raise SystemExit(2)
PY
)
  set -- $coords
  adb shell input tap "$1" "$2"
}

# 1) Local-first proof: an unreachable remote brain must not break a deterministic Android action.
adb reverse --remove "tcp:$PORT"
seed ""
adb shell am force-stop "$PACKAGE" || true
adb logcat -c
start_command "I'm good. Can you do me a favor and open settings, please?" | tee "$OUTPUT/remote-integration-local-offline.txt"
LOCAL_OK=0
for i in $(seq 1 20); do
  adb shell dumpsys activity activities > "$OUTPUT/remote-integration-local-offline-activity.txt" || true
  if grep -Eq 'topResumedActivity=.*com\.jarvis\.mobile/\.SettingsActivity' "$OUTPUT/remote-integration-local-offline-activity.txt"; then LOCAL_OK=1; break; fi
  sleep 1
done
test "$LOCAL_OK" -eq 1
adb reverse "tcp:$PORT" "tcp:$PORT"

# 2) Complex/unhandled request goes through authenticated submit and immediately acknowledges.
seed ""
adb shell am force-stop "$PACKAGE" || true
adb logcat -c
start_command 'Research two viable approaches for a multi-step JARVIS project and prepare a recommendation with tradeoffs' | tee "$OUTPUT/remote-integration-submit-launch.txt"
ACK=0
for i in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/remote-integration-submit-logcat.txt" || true
  if grep -q "I've started that and I'll keep you updated" "$OUTPUT/remote-integration-submit-logcat.txt"; then ACK=1; break; fi
  sleep 1
done
test "$ACK" -eq 1
grep -q 'POST /v1/goals auth=Bearer emulator-secret' "$LOG"
grep -q 'Research two viable approaches' "$LOG"

# 3) An expired reconnect cursor must preserve the project, discard only that cursor, and resync.
seed project-expired expired-emulator
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$ACTIVITY" > "$OUTPUT/remote-integration-expired-launch.txt"
EXPIRED_RESYNC=0
for i in $(seq 1 30); do
  dump_ui remote-integration-expired
  if grep -q 'still working on that' "$OUTPUT/remote-integration-expired.xml"; then EXPIRED_RESYNC=1; break; fi
  sleep 1
done
test "$EXPIRED_RESYNC" -eq 1
grep -q 'GET /v1/projects/project-expired/events?after_event_id=expired-emulator auth=Bearer emulator-secret' "$LOG"
grep -q 'GET /v1/projects/project-expired/events auth=Bearer emulator-secret' "$LOG"
adb exec-out screencap -p > "$OUTPUT/jarvis-remote-expired-resync.png"

# 4) A backend-confirmed missing project is terminal for the local bookmark. Reopening the APK must
# not hammer the same 404 forever; the server should see exactly one project lookup across two launches.
seed project-missing
MISSING_BEFORE=$(grep -c 'GET /v1/projects/project-missing auth=Bearer emulator-secret' "$LOG" || true)
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$ACTIVITY" > "$OUTPUT/remote-integration-missing-first-launch.txt"
MISSING_SEEN=0
for i in $(seq 1 20); do
  MISSING_NOW=$(grep -c 'GET /v1/projects/project-missing auth=Bearer emulator-secret' "$LOG" || true)
  if [ "$MISSING_NOW" -eq $((MISSING_BEFORE + 1)) ]; then MISSING_SEEN=1; break; fi
  sleep 1
done
test "$MISSING_SEEN" -eq 1
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$ACTIVITY" > "$OUTPUT/remote-integration-missing-second-launch.txt"
sleep 2
MISSING_AFTER=$(grep -c 'GET /v1/projects/project-missing auth=Bearer emulator-secret' "$LOG" || true)
test "$MISSING_AFTER" -eq $((MISSING_BEFORE + 1))

# 5) Reconnect discovers the exact opaque approval ID and exposes normal JARVIS decision controls.
seed project-approval
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$ACTIVITY" > "$OUTPUT/remote-integration-approval-launch.txt"
APPROVAL_UI=0
for i in $(seq 1 30); do
  dump_ui remote-integration-approval
  if grep -q 'needs your approval' "$OUTPUT/remote-integration-approval.xml" && grep -q 'JARVIS APPROVE action' "$OUTPUT/remote-integration-approval.xml"; then APPROVAL_UI=1; break; fi
  sleep 1
done
test "$APPROVAL_UI" -eq 1
adb exec-out screencap -p > "$OUTPUT/jarvis-remote-approval.png"
tap_desc "$OUTPUT/remote-integration-approval.xml" 'JARVIS APPROVE action'
sleep 2
grep -q 'POST /v1/projects/project-approval/approvals/approval-emulator auth=Bearer emulator-secret' "$LOG"
grep -q '"approved":true' "$LOG"

# Reopen the same project after approval and prove its final result reaches ordinary JARVIS UI.
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$ACTIVITY" > "$OUTPUT/remote-integration-result-launch.txt"
RESULT_UI=0
for i in $(seq 1 30); do
  dump_ui remote-integration-result
  if grep -q 'APPROVAL_FINAL_RESULT_VISIBLE' "$OUTPUT/remote-integration-result.xml"; then RESULT_UI=1; break; fi
  sleep 1
done
test "$RESULT_UI" -eq 1
adb exec-out screencap -p > "$OUTPUT/jarvis-remote-integration-result.png"

# 6) Separate active project exposes CANCEL; cancellation must be backend-confirmed before local state clears.
seed project-cancel
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$ACTIVITY" > "$OUTPUT/remote-integration-cancel-launch.txt"
CANCEL_UI=0
for i in $(seq 1 30); do
  dump_ui remote-integration-cancel
  if grep -q 'still working on that' "$OUTPUT/remote-integration-cancel.xml" && grep -q 'JARVIS CANCEL action' "$OUTPUT/remote-integration-cancel.xml"; then CANCEL_UI=1; break; fi
  sleep 1
done
test "$CANCEL_UI" -eq 1
adb exec-out screencap -p > "$OUTPUT/jarvis-remote-cancel.png"
tap_desc "$OUTPUT/remote-integration-cancel.xml" 'JARVIS CANCEL action'
sleep 2
grep -q 'POST /v1/projects/project-cancel/cancel auth=Bearer emulator-secret' "$LOG"
dump_ui remote-integration-cancelled
grep -q 'Cancelled, sir' "$OUTPUT/remote-integration-cancelled.xml"

# UI proof must remain provider/worker-neutral.
! grep -Eiq 'agent[ _-]?zero|valkey|ollama|anthropic|openai|claude|chatgpt|management plane' \
  "$OUTPUT/remote-integration-expired.xml" "$OUTPUT/remote-integration-approval.xml" "$OUTPUT/remote-integration-result.xml" "$OUTPUT/remote-integration-cancel.xml" "$OUTPUT/remote-integration-cancelled.xml"

echo 'Remote goal full integration emulator proof GREEN'
