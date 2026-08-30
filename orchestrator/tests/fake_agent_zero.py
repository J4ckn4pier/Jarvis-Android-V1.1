from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class AgentZeroStubHandler(BaseHTTPRequestHandler):
    server_version = "JARVIS-AgentZero-CI-Stub/1.0"

    def _send_json(self, status: int, payload: dict[str, object]) -> None:
        raw = json.dumps(payload, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self) -> None:
        if self.path == "/api/health":
            self._send_json(200, {"status": "ok"})
            return
        self._send_json(404, {"error": "not found"})

    def do_POST(self) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        if self.headers.get("X-API-KEY") != "ci-agent-key":
            self._send_json(401, {"error": "unauthorized"})
            return
        if self.path == "/api/api_message":
            continuing = bool(body.get("context_id"))
            context_id = str(body.get("context_id") or "ctx-ci")
            mode = "continued" if continuing else "new"
            self._send_json(
                200,
                {
                    "response": f"Agent Zero stub {mode}: {body.get('message', '')}",
                    "context_id": context_id,
                },
            )
            return
        if self.path in {"/api/api_reset_chat", "/api/api_terminate_chat"}:
            self._send_json(200, {"success": True, "context_id": body.get("context_id")})
            return
        self._send_json(404, {"error": "not found"})

    def log_message(self, format: str, *args: object) -> None:
        return


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8001), AgentZeroStubHandler).serve_forever()
