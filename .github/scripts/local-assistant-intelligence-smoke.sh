#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
PORT=18081
SERVER_LOG="$OUTPUT/local-assistant-intelligence-stub.txt"
STUB_PID=""

cleanup() {
  if [ -n "$STUB_PID" ]; then
    kill "$STUB_PID" >/dev/null 2>&1 || true
    wait "$STUB_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

python3 - "$PORT" "$SERVER_LOG" <<'PY' &
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

port = int(sys.argv[1])
log_path = sys.argv[2]

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        return

    def _send(self, status, payload):
        raw = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        self._send(200 if self.path == "/health" else 404, {"ok": self.path == "/health"})

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length).decode("utf-8"))
            assert self.path == "/v1/chat/completions"
            assert body.get("model") == "jarvis-ci-local"
            messages = body.get("messages") or []
            user_content = "\n".join(str(m.get("content", "")) for m in messages if m.get("role") == "user")
            # This phrase deliberately does not match SemanticGoalInterpreter's literal "open settings" cues.
            assert "I need to change how you work; could you bring up the place where I configure you?" in user_content
            schema = ((body.get("response_format") or {}).get("json_schema") or {}).get("schema") or {}
            tool_enum = (((schema.get("properties") or {}).get("steps") or {}).get("items") or {}).get("properties", {}).get("tool", {}).get("enum", [])
            assert "open_jarvis_settings" in tool_enum
            with open(log_path, "a", encoding="utf-8") as handle:
                handle.write("LOCAL_ASSISTANT_STUB_RECEIVED_NATURAL_LANGUAGE\n")
            payload = {
                "answer": "Certainly, sir.",
                "goal": "Open JARVIS settings",
                "steps": [{"tool": "open_jarvis_settings", "arguments": []}],
            }
            self._send(200, {"choices": [{"message": {"content": json.dumps(payload)}}]})
        except Exception as exc:
            with open(log_path, "a", encoding="utf-8") as handle:
                handle.write("LOCAL_ASSISTANT_STUB_FAIL " + repr(exc) + "\n")
            self._send(400, {"error": str(exc)})

ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
PY
STUB_PID=$!

for attempt in $(seq 1 30); do
  curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null && break
  sleep 1
done
curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null

adb logcat -c
adb shell am broadcast --receiver-foreground \
  -a com.jarvis.mobile.DEBUG_TEST_ASSISTANT_INTELLIGENCE \
  -p "$PACKAGE" \
  --es endpoint "http://10.0.2.2:$PORT/v1/chat/completions" \
  --es model "jarvis-ci-local" \
  --es command "I need to change how you work; could you bring up the place where I configure you?" \
  | tee "$OUTPUT/emulator-local-assistant-intelligence-broadcast.txt"

PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-local-assistant-intelligence-logcat.txt"
  if grep -q 'LOCAL_ASSISTANT_INTELLIGENCE_FAIL' "$OUTPUT/emulator-local-assistant-intelligence-logcat.txt"; then
    cat "$OUTPUT/emulator-local-assistant-intelligence-logcat.txt"
    cat "$SERVER_LOG" || true
    exit 1
  fi
  if grep -q 'LOCAL_ASSISTANT_INTELLIGENCE_PASS' "$OUTPUT/emulator-local-assistant-intelligence-logcat.txt"; then
    PASSED=1
    break
  fi
  sleep 1
done

test "$PASSED" -eq 1
grep -q 'LOCAL_ASSISTANT_STUB_RECEIVED_NATURAL_LANGUAGE' "$SERVER_LOG"
