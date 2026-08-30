from __future__ import annotations

import pytest

from jarvis_orchestrator import app as app_module
from jarvis_orchestrator.core import BrainEvent
from jarvis_orchestrator.identity import scope_session_id


class HeaderWebSocket:
    def __init__(self, *, authorization: str | None, session_id: str = "primary") -> None:
        self.query_params = {"session_id": session_id}
        self.headers = {"authorization": authorization} if authorization else {}
        self.accepted = False
        self.closed_code: int | None = None
        self.sent: list[dict[str, object]] = []

    async def accept(self) -> None:
        self.accepted = True

    async def close(self, code: int) -> None:
        self.closed_code = code

    async def send_json(self, payload: dict[str, object]) -> None:
        self.sent.append(payload)


class OneEventBus:
    def __init__(self, session_id: str) -> None:
        self.session_id = session_id

    async def subscribe(self):
        yield BrainEvent(self.session_id, "task-1", "IDLE", 0, "Complete", 4, 1.0)
        raise RuntimeError("end test stream")


@pytest.mark.asyncio
async def test_event_websocket_accepts_bearer_header_without_query_token(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    scoped = scope_session_id("alice", "primary")
    monkeypatch.setattr(app_module.app.state, "bus", OneEventBus(scoped), raising=False)
    ws = HeaderWebSocket(authorization="Bearer token-a")

    await app_module.events(ws)

    assert ws.accepted is True
    assert ws.closed_code is None
    assert ws.sent[0]["session_id"] == "primary"


@pytest.mark.asyncio
async def test_event_websocket_rejects_invalid_bearer_header(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    ws = HeaderWebSocket(authorization="Bearer wrong")

    await app_module.events(ws)

    assert ws.accepted is False
    assert ws.closed_code == 4401
