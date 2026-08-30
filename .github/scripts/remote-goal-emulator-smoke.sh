#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
ACTIVITY="$PACKAGE/com.jarvis.mobile.MainActivity"
MOCK_PORT=18080
MOCK_LOG="/tmp/jarvis-remote-goal-mock.log"
MOCK_MODE="/tmp/jarvis-remote-goal-mock-mode"
MOCK_PID="/tmp/jarvis-remote-goal-mock.pid"

cleanup() {
  adb reverse --remove "tcp:$MOCK_PORT" >/dev/null 2>&1 || true
  if [ -f "$MOCK_PID" ]; then kill "$(cat "$MOCK_PID")" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

: > "$MOCK_LOG"
echo progress > "$MOCK_MODE"

python3 - "$MOCK_PORT" "$MOCK_LOG" "$MOCK_MODE" <<'PY' &
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

port = int(sys.argv[1])
log_path = sys.argv[2]
mode_path = sys.argv[3]

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        return

    def _write(self, payload):
        body = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        with open(log_path, "a", encoding="utf-8") as log:
            log.write(f"GET {self.path} auth={self.headers.get('Authorization', '')}\n")
        mode = open(mode_path, encoding="utf-8").read().strip()
        if self.path.startswith("/v1/projects/project-emulator/events"):
            self._write({
                "project_id": "project-emulator",
                "events": [{
                    "event_id": "evt-1",
                    "project_id": "project-emulator",
                    "kind": "task_progress",
                    "task_id": "task-1",
                    "timestamp": "2026-08-30T20:00:00Z"
                }],
                "next_event_id": "evt-1",
                "has_more": False
            })
        elif self.path == "/v1/projects/project-emulator/result":
            self._write({
                "project_id": "project-emulator",
                "state": "completed",
                "result": "REMOTE_RESULT_VISIBLE",
                "provider_details_exposed": False
            })
        elif self.path == "/v1/projects/project-emulator":
            completed = mode == "completed"
            self._write({
                "project_id": "project-emulator",
                "session_id": "primary",
                "goal": "verify foreground reconnect",
                "state": "completed" if completed else "active",
                "task_count": 2,
                "task_states": {"completed": 2 if completed else 1},
                "last_progress_at": "2026-08-30T20:00:00Z",
                "provider_details_exposed": False
            })
        else:
            self.send_error(404)

ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
PY
echo $! > "$MOCK_PID"
sleep 1
adb reverse "tcp:$MOCK_PORT" "tcp:$MOCK_PORT"

# A debug-only receiver seeds encrypted connection + durable project state inside the app process.
adb shell am broadcast \
  -a com.jarvis.mobile.DEBUG_SEED_REMOTE_GOAL \
  -n com.jarvis.mobile/.remote.RemoteGoalStateTestReceiver \
  --es base_url "http://127.0.0.1:$MOCK_PORT" \
  --es token "emulator-secret" \
  --es project_id "project-emulator" \
  | tee "$OUTPUT/remote-goal-seed.txt"
grep -q 'Broadcast completed: result=1' "$OUTPUT/remote-goal-seed.txt"

# First foreground: same saved project is resumed and progress appears through the normal HUD.
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$ACTIVITY" > "$OUTPUT/remote-goal-progress-launch.txt"
PROGRESS_VISIBLE=0
for attempt in $(seq 1 30); do
  adb shell uiautomator dump /sdcard/jarvis-remote-progress.xml >/dev/null 2>&1 || true
  if adb pull /sdcard/jarvis-remote-progress.xml "$OUTPUT/jarvis-remote-progress.xml" >/dev/null 2>&1 \
      && grep -q 'still working on that' "$OUTPUT/jarvis-remote-progress.xml" \
      && grep -q 'Progress: 1 of 2 steps complete' "$OUTPUT/jarvis-remote-progress.xml"; then
    PROGRESS_VISIBLE=1
    break
  fi
  sleep 1
done
test "$PROGRESS_VISIBLE" -eq 1
adb exec-out screencap -p > "$OUTPUT/jarvis-remote-progress.png"
grep -q 'GET /v1/projects/project-emulator auth=Bearer emulator-secret' "$MOCK_LOG"
grep -q 'GET /v1/projects/project-emulator/events auth=Bearer emulator-secret' "$MOCK_LOG"

# Second foreground: persisted cursor is reused and a completed result appears in the same JARVIS UI.
echo completed > "$MOCK_MODE"
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$ACTIVITY" > "$OUTPUT/remote-goal-result-launch.txt"
RESULT_VISIBLE=0
for attempt in $(seq 1 30); do
  adb shell uiautomator dump /sdcard/jarvis-remote-result.xml >/dev/null 2>&1 || true
  if adb pull /sdcard/jarvis-remote-result.xml "$OUTPUT/jarvis-remote-result.xml" >/dev/null 2>&1 \
      && grep -q 'REMOTE_RESULT_VISIBLE' "$OUTPUT/jarvis-remote-result.xml"; then
    RESULT_VISIBLE=1
    break
  fi
  sleep 1
done
test "$RESULT_VISIBLE" -eq 1
adb exec-out screencap -p > "$OUTPUT/jarvis-remote-result.png"
grep -q 'GET /v1/projects/project-emulator/events?after_event_id=evt-1 auth=Bearer emulator-secret' "$MOCK_LOG"
grep -q 'GET /v1/projects/project-emulator/result auth=Bearer emulator-secret' "$MOCK_LOG"
! grep -Eiq 'agent[ _-]?zero|valkey|ollama|anthropic|openai|claude|chatgpt|management plane' \
  "$OUTPUT/jarvis-remote-progress.xml" "$OUTPUT/jarvis-remote-result.xml"

echo 'Remote foreground reconnect emulator proof GREEN'
