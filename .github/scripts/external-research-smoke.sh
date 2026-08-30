#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
PORT=18081
SERVER_LOG="$OUTPUT/external-research-stub.txt"
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

    def send_json(self, status, payload):
        data = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            self.send_json(200, {"ok": True})
        else:
            self.send_json(404, {"error": "not found"})

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length).decode("utf-8"))
            assert body.get("operation") == "discover_places", body
            args = body.get("arguments") or {}
            assert args.get("category") == "CI_RESEARCH_MARKER_271828", args
            with open(log_path, "a", encoding="utf-8") as fh:
                fh.write("JARVIS_RESEARCH_STUB_PASS CI_RESEARCH_MARKER_271828\n")
            self.send_json(200, {
                "payload": "CI_RESEARCH_MARKER_271828",
                "source": "jarvis-ci-research-stub",
                "observed_at": "2026-08-30T07:00:00Z",
                "confidence": 1.0
            })
        except Exception as exc:
            with open(log_path, "a", encoding="utf-8") as fh:
                fh.write("JARVIS_RESEARCH_STUB_FAIL " + repr(exc) + "\n")
            self.send_json(400, {"error": str(exc)})

ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
PY
STUB_PID=$!

READY=0
for attempt in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null; then READY=1; break; fi
  sleep 1
done
test "$READY" -eq 1

adb logcat -c
adb shell am broadcast --receiver-foreground \
  -a com.jarvis.mobile.DEBUG_TEST_EXTERNAL_RESEARCH \
  -p "$PACKAGE" \
  --es endpoint "http://10.0.2.2:$PORT/research" \
  | tee "$OUTPUT/emulator-external-research-broadcast.txt"

PASSED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-external-research-logcat.txt"
  if grep -q 'JARVIS_RESEARCH_TEST_FAIL' "$OUTPUT/emulator-external-research-logcat.txt"; then
    grep -A 20 'JARVIS_RESEARCH_TEST_FAIL' "$OUTPUT/emulator-external-research-logcat.txt" || true
    cat "$SERVER_LOG" || true
    exit 1
  fi
  if grep -q 'JARVIS_RESEARCH_TEST_PASS.*CI_RESEARCH_MARKER_271828.*jarvis-ci-research-stub.*2026-08-30T07:00:00Z' "$OUTPUT/emulator-external-research-logcat.txt"; then
    PASSED=1
    break
  fi
  sleep 1
done

if [ "$PASSED" -ne 1 ]; then
  echo '--- JARVIS external research trace ---'
  grep -E 'JARVIS_RESEARCH_TEST|AndroidRuntime|FATAL EXCEPTION|Cleartext|NetworkSecurity' "$OUTPUT/emulator-external-research-logcat.txt" || true
  cat "$SERVER_LOG" || true
fi

test "$PASSED" -eq 1
grep -q 'JARVIS_RESEARCH_STUB_PASS CI_RESEARCH_MARKER_271828' "$SERVER_LOG"
