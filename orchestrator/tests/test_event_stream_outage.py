from __future__ import annotations

import pytest
from redis.exceptions import ConnectionError as RedisConnectionError

from jarvis_orchestrator import app as app_module


class RecordingWebSocket:
    def __init__(self) -> None:
        self.query_params = {"session_id": "primary"}
        self.accepted = False
        self.closed_code: int | None = None
        self.sent: list[dict[str, object]] = []

    async def accept(self) -> None:
        self.accepted = True

    async def close(self, code: int) -> None:
        self.closed_code = code

    async def send_json(self, payload: dict[str, object]) -> None:
        self.sent.append(payload)


class FailingSubscriptionBus:
    async def subscribe(self):
        if False:
            yield None
        raise RedisConnectionError("pubsub connection lost")


@pytest.mark.asyncio
async def test_event_socket_reports_retryable_state_outage_before_closing(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.setattr(app_module.app.state, "bus", FailingSubscriptionBus(), raising=False)
    ws = RecordingWebSocket()

    await app_module.events(ws)

    assert ws.accepted is True
    assert ws.sent == [
        {
            "error": "State backend unavailable",
            "code": "state_backend_unavailable",
            "retryable": True,
        }
    ]
    assert ws.closed_code == 1013
