from __future__ import annotations

import pytest
from fastapi import WebSocketDisconnect

from jarvis_orchestrator import app as app_module


class RecordingOrchestrator:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str, str | None]] = []

    async def submit(self, text: str, session_id: str, request_id: str | None = None):
        self.calls.append((text, session_id, request_id))
        return {"task_id": "task-1", "response": "ok"}


class FakeWebSocket:
    def __init__(self, query_params: dict[str, str], incoming: list[dict[str, object]] | None = None) -> None:
        self.query_params = query_params
        self.headers: dict[str, str] = {}
        self.incoming = list(incoming or [])
        self.accepted = False
        self.closed_code: int | None = None
        self.sent: list[dict[str, object]] = []

    async def accept(self) -> None:
        self.accepted = True

    async def close(self, code: int) -> None:
        self.closed_code = code

    async def send_json(self, payload: dict[str, object]) -> None:
        self.sent.append(payload)

    async def receive_json(self) -> dict[str, object]:
        if not self.incoming:
            raise WebSocketDisconnect()
        return self.incoming.pop(0)


class NoEventBus:
    async def subscribe(self):
        if False:
            yield None
        raise RuntimeError("test stream ended")


@pytest.mark.asyncio
async def test_event_socket_rejects_session_id_with_edge_whitespace(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.setattr(app_module.app.state, "bus", NoEventBus(), raising=False)
    ws = FakeWebSocket({"session_id": " primary "})

    await app_module.events(ws)

    assert ws.accepted is False
    assert ws.closed_code == 4400


@pytest.mark.asyncio
async def test_input_socket_rejects_session_id_with_edge_whitespace_before_dispatch(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    orchestrator = RecordingOrchestrator()
    monkeypatch.setattr(app_module.app.state, "orchestrator", orchestrator, raising=False)
    ws = FakeWebSocket(
        {},
        incoming=[{"text": "hello", "session_id": " primary "}],
    )

    await app_module.input_socket(ws)

    assert orchestrator.calls == []
    assert ws.sent == [
        {"error": "session_id must not have leading or trailing whitespace"}
    ]
