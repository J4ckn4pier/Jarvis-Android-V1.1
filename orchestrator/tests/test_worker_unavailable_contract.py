from __future__ import annotations

import pytest
from fastapi import HTTPException, WebSocketDisconnect

from jarvis_orchestrator import app as app_module
from jarvis_orchestrator.runtime import WorkerUnavailableError


class UnavailableOrchestrator:
    async def submit(self, text: str, session_id: str, request_id: str | None = None):
        raise WorkerUnavailableError("Agent Zero unavailable")


class FakeWebSocket:
    def __init__(self, incoming: list[dict[str, object]]) -> None:
        self.query_params: dict[str, str] = {}
        self.headers: dict[str, str] = {}
        self.incoming = list(incoming)
        self.accepted = False
        self.sent: list[dict[str, object]] = []

    async def accept(self) -> None:
        self.accepted = True

    async def close(self, code: int) -> None:
        pass

    async def send_json(self, payload: dict[str, object]) -> None:
        self.sent.append(payload)

    async def receive_json(self) -> dict[str, object]:
        if not self.incoming:
            raise WebSocketDisconnect()
        return self.incoming.pop(0)


@pytest.mark.asyncio
async def test_rest_command_maps_worker_outage_to_service_unavailable(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.setattr(app_module.app.state, "orchestrator", UnavailableOrchestrator(), raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.command(
            app_module.Command(text="hello", session_id="primary", request_id="phone-1"),
            authorization=None,
        )

    assert exc.value.status_code == 503
    assert exc.value.detail == "Worker runtime unavailable"


@pytest.mark.asyncio
async def test_websocket_command_returns_structured_worker_unavailable_without_closing(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.setattr(app_module.app.state, "orchestrator", UnavailableOrchestrator(), raising=False)
    ws = FakeWebSocket([
        {"text": "hello", "session_id": "primary", "request_id": "phone-1"},
    ])

    await app_module.input_socket(ws)

    assert ws.accepted is True
    assert ws.sent == [
        {
            "error": "Worker runtime unavailable",
            "code": "worker_unavailable",
            "request_id": "phone-1",
            "session_id": "primary",
            "retryable": True,
        }
    ]
