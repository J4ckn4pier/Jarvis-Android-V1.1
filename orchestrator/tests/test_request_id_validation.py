from __future__ import annotations

import pytest
from fastapi import HTTPException, WebSocketDisconnect

from jarvis_orchestrator import app as app_module


def test_request_id_rejects_edge_whitespace_instead_of_normalizing():
    with pytest.raises(HTTPException) as exc:
        app_module._validated_request_id(" phone-42 ")

    assert exc.value.status_code == 422
    assert exc.value.detail == "request_id must not have leading or trailing whitespace"


def test_request_id_preserves_exact_valid_value():
    assert app_module._validated_request_id("phone-42") == "phone-42"


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


@pytest.mark.asyncio
async def test_http_command_rejects_ambiguous_request_id_before_dispatch(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    orchestrator = RecordingOrchestrator()
    monkeypatch.setattr(app_module.app.state, "orchestrator", orchestrator, raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.command(
            app_module.Command(text="hello", session_id="primary", request_id=" phone-42 "),
            authorization=None,
        )

    assert exc.value.status_code == 422
    assert exc.value.detail == "request_id must not have leading or trailing whitespace"
    assert orchestrator.calls == []


@pytest.mark.asyncio
async def test_input_socket_rejects_ambiguous_request_id_before_dispatch(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    orchestrator = RecordingOrchestrator()
    monkeypatch.setattr(app_module.app.state, "orchestrator", orchestrator, raising=False)
    ws = FakeWebSocket(
        [{"text": "hello", "session_id": "primary", "request_id": " phone-42 "}]
    )

    await app_module.input_socket(ws)

    assert ws.accepted is True
    assert orchestrator.calls == []
    assert ws.sent == [{"error": "request_id must not have leading or trailing whitespace"}]
