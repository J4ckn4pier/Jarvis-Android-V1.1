from __future__ import annotations

import pytest
from fastapi import HTTPException, WebSocketDisconnect

from jarvis_orchestrator import app as app_module
from jarvis_orchestrator.identity import scope_session_id


class RecordingOrchestrator:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str, str | None]] = []

    async def submit(self, text: str, session_id: str, request_id: str | None = None):
        self.calls.append((text, session_id, request_id))
        return {"session_id": session_id, "task_id": "task-1", "response": "ok"}


class FakeWebSocket:
    def __init__(self, incoming: list[dict[str, object]]) -> None:
        self.query_params: dict[str, str] = {}
        self.headers: dict[str, str] = {}
        self.incoming = list(incoming)
        self.sent: list[dict[str, object]] = []
        self.accepted = False

    async def accept(self) -> None:
        self.accepted = True

    async def close(self, code: int) -> None:
        return None

    async def send_json(self, payload: dict[str, object]) -> None:
        self.sent.append(payload)

    async def receive_json(self) -> dict[str, object]:
        if not self.incoming:
            raise WebSocketDisconnect()
        return self.incoming.pop(0)


def _open_auth(monkeypatch) -> None:
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)


@pytest.mark.asyncio
async def test_input_socket_preserves_exact_command_text_for_cross_transport_retry(monkeypatch):
    _open_auth(monkeypatch)
    orchestrator = RecordingOrchestrator()
    monkeypatch.setattr(app_module.app.state, "orchestrator", orchestrator, raising=False)
    ws = FakeWebSocket(
        [{"text": " hello ", "session_id": "primary", "request_id": "phone-42"}]
    )

    await app_module.input_socket(ws)

    assert orchestrator.calls == [
        (" hello ", scope_session_id("owner", "primary"), "phone-42")
    ]


@pytest.mark.asyncio
async def test_http_command_rejects_whitespace_only_text_before_dispatch(monkeypatch):
    _open_auth(monkeypatch)
    orchestrator = RecordingOrchestrator()
    monkeypatch.setattr(app_module.app.state, "orchestrator", orchestrator, raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.command(
            app_module.Command(text="   ", session_id="primary", request_id="phone-42"),
            authorization=None,
        )

    assert exc.value.status_code == 422
    assert exc.value.detail == "text must contain at least one non-whitespace character"
    assert orchestrator.calls == []
