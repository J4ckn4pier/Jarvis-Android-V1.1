from __future__ import annotations

import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module
from jarvis_orchestrator.core import BrainEvent


class HistoryBus:
    async def history(self, session_id: str, limit: int = 100):
        return [BrainEvent(session_id, "task-123", "IDLE", 0, "Complete", 4, 10.0)]


class FiniteBus:
    async def subscribe(self):
        yield BrainEvent("owner:primary", "task-456", "LANGUAGE", 48, "Preparing response", 3, 20.0)
        raise RuntimeError("end test stream")


class MissingCursorBus:
    async def history(
        self,
        session_id: str,
        limit: int = 100,
        after_event_id: str | None = None,
    ):
        return []

    async def contains_event(self, session_id: str, event_id: str) -> bool:
        return False


class FakeWebSocket:
    def __init__(self) -> None:
        self.query_params = {"session_id": "primary"}
        self.accepted = False
        self.sent: list[dict[str, object]] = []

    async def accept(self) -> None:
        self.accepted = True

    async def close(self, code: int) -> None:
        raise AssertionError(f"unexpected close: {code}")

    async def send_json(self, payload: dict[str, object]) -> None:
        self.sent.append(payload)


@pytest.mark.asyncio
async def test_history_exposes_stable_event_id_for_reconnect_deduplication(monkeypatch):
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_REQUIRE_AUTH", raising=False)
    monkeypatch.setattr(app_module.app.state, "bus", HistoryBus(), raising=False)

    payload = await app_module.event_history("primary", limit=100, authorization=None)

    assert payload["events"][0]["event_id"] == "task-123:4"


@pytest.mark.asyncio
async def test_reconnect_rejects_cursor_outside_retained_history(monkeypatch):
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_REQUIRE_AUTH", raising=False)
    monkeypatch.setattr(app_module.app.state, "bus", MissingCursorBus(), raising=False)

    with pytest.raises(HTTPException) as exc_info:
        await app_module.event_history(
            "primary",
            limit=100,
            after_event_id="old-task:4",
            authorization=None,
        )

    assert exc_info.value.status_code == 410
    assert exc_info.value.detail == "Recovery cursor is no longer available"


@pytest.mark.asyncio
async def test_reconnect_rejects_cursor_with_edge_whitespace_as_invalid_input(monkeypatch):
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_REQUIRE_AUTH", raising=False)
    monkeypatch.setattr(app_module.app.state, "bus", MissingCursorBus(), raising=False)

    with pytest.raises(HTTPException) as exc_info:
        await app_module.event_history(
            "primary",
            limit=100,
            after_event_id=" task-123:4 ",
            authorization=None,
        )

    assert exc_info.value.status_code == 422
    assert exc_info.value.detail == "after_event_id must not have leading or trailing whitespace"


@pytest.mark.asyncio
async def test_live_events_use_same_stable_event_id_as_history(monkeypatch):
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_REQUIRE_AUTH", raising=False)
    monkeypatch.setattr(app_module.app.state, "bus", FiniteBus(), raising=False)
    monkeypatch.setattr(app_module, "_scoped_session", lambda principal, public: "owner:primary")
    ws = FakeWebSocket()

    await app_module.events(ws)

    assert ws.accepted is True
    assert ws.sent[0]["event_id"] == "task-456:3"
