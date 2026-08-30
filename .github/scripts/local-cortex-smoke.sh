#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
PORT=18080
SERVER_LOG="$OUTPUT/local-cortex-stub.txt"
STUB_PID=""

cleanup() {
  if [ -n "$STUB_PID" ]; then
    kill "$STUB_PID" >/dev/null 2>&1 || true
    wait "$STUB_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

# A deterministic, $0 local stub exercises the exact OpenAI-compatible HTTP/JSON boundary from
# inside the Android emulator. 10.0.2.2 is the emulator's host-loopback alias; production still
# rejects arbitrary cleartext endpoints through network_security_config.xml.
python3 - "$PORT" "$SERVER_LOG" <<'PY' &
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

port = int(sys.argv[1])
log_path = sys.argv[2]

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        return

    def _send_json(self, status, payload):
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self):
        if self.path == "/health":
            self._send_json(200, {"ok": True})
        else:
            self._send_json(404, {"error": "not found"})

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length).decode("utf-8"))
            assert self.path == "/v1/chat/completions", self.path
            assert body.get("model") == "jarvis-ci-local", body.get("model")
            assert body.get("stream") is False, body.get("stream")
            response_format = body.get("response_format") or {}
            assert response_format.get("type") == "json_schema", response_format
            messages = body.get("messages") or []
            user_content = "\n".join(
                str(message.get("content", "")) for message in messages
                if message.get("role") == "user"
            )
            assert "CI_CONTEXT_MARKER_314159" in user_content, user_content
            with open(log_path, "a", encoding="utf-8") as handle:
                handle.write("JARVIS_LOCAL_CORTEX_STUB_PASS context=CI_CONTEXT_MARKER_314159\n")
            provider_payload = {
                "answer": "Local cortex transport verified.",
                "goal": "",
                "steps": [],
            }
            self._send_json(200, {
                "choices": [{"message": {"content": json.dumps(provider_payload)}}]
            })
        except Exception as exc:
            with open(log_path, "a", encoding="utf-8") as handle:
                handle.write("JARVIS_LOCAL_CORTEX_STUB_FAIL " + repr(exc) + "\n")
            self._send_json(400, {"error": str(exc)})

ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
PY
STUB_PID=$!

SERVER_READY=0
for attempt in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null; then
    SERVER_READY=1
    break
  fi
  sleep 1
done
test "$SERVER_READY" -eq 1

adb logcat -c
adb shell am broadcast --receiver-foreground \
  -a com.jarvis.mobile.DEBUG_TEST_LOCAL_CORTEX \
  -p "$PACKAGE" \
  --es endpoint "http://10.0.2.2:$PORT/v1/chat/completions" \
  --es model "jarvis-ci-local" \
  | tee "$OUTPUT/emulator-local-cortex-broadcast.txt"

CORTEX_PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-local-cortex-logcat.txt"
  if grep -q 'JARVIS_LOCAL_CORTEX_TEST_FAIL' "$OUTPUT/emulator-local-cortex-logcat.txt"; then
    grep -A 30 'JARVIS_LOCAL_CORTEX_TEST_FAIL' "$OUTPUT/emulator-local-cortex-logcat.txt" || true
    cat "$SERVER_LOG" || true
    exit 1
  fi
  if grep -q 'JARVIS_LOCAL_CORTEX_TEST_PASS.*Local cortex transport verified' "$OUTPUT/emulator-local-cortex-logcat.txt"; then
    CORTEX_PASSED=1
    break
  fi
  sleep 1
done

if [ "$CORTEX_PASSED" -ne 1 ]; then
  echo '--- JARVIS local cortex trace ---'
  grep -E 'JARVIS_LOCAL_CORTEX_TEST|AndroidRuntime|FATAL EXCEPTION|Cleartext|NetworkSecurity' "$OUTPUT/emulator-local-cortex-logcat.txt" || true
  cat "$SERVER_LOG" || true
fi

test "$CORTEX_PASSED" -eq 1
grep -q 'JARVIS_LOCAL_CORTEX_STUB_PASS context=CI_CONTEXT_MARKER_314159' "$SERVER_LOG"
