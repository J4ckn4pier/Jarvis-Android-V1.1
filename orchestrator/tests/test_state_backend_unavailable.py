from __future__ import annotations

import pytest
from fastapi import HTTPException, WebSocketDisconnect
from redis.exceptions import ConnectionError as RedisConnectionError

from jarvis_orchestrator import app as app_module


class OfflineStateOrchestrator:
    async def submit(self, text: str, session_id: str, request_id: str | None = None):
        raise RedisConnectionError("state backend unavailable")

    async def run_session_operation(self, session_id: str, operation):
        raise RedisConnectionError("state backend unavailable")


class OfflineHistoryBus:
    async def history(self, session_id: str, limit: int, after_event_id: str | None = None):
        raise RedisConnectionError("state backend unavailable")


class LifecycleRuntime:
    async def reset(self, session_id: str) -> bool:
        return True


class FakeWebSocket:
    def __init__(self) -> None:
        self.query_params: dict[str, str] = {}
        self.headers: dict[str, str] = {}
        self.sent: list[dict[str, object]] = []
        self._incoming = [{"text": "hello", "session_id": "primary", "request_id": "phone-1"}]

    async def accept(self) -> None:
        pass

    async def close(self, code: int) -> None:
        pass

    async def receive_json(self) -> dict[str, object]:
        if not self._incoming:
            raise WebSocketDisconnect()
        return self._incoming.pop(0)

    async def send_json(self, payload: dict[str, object]) -> None:
        self.sent.append(payload)


def _open_auth(monkeypatch) -> None:
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.delenv("JARVIS_REQUIRE_AUTH", raising=False)


@pytest.mark.asyncio
async def test_http_command_maps_state_backend_outage_to_503(monkeypatch):
    _open_auth(monkeypatch)
    monkeypatch.setattr(app_module.app.state, "orchestrator", OfflineStateOrchestrator(), raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.command(app_module.Command(text="hello"), authorization=None)

    assert exc.value.status_code == 503
    assert exc.value.detail == "State backend unavailable"


@pytest.mark.asyncio
async def test_input_socket_reports_retryable_state_backend_outage(monkeypatch):
    _open_auth(monkeypatch)
    monkeypatch.setattr(app_module.app.state, "orchestrator", OfflineStateOrchestrator(), raising=False)
    ws = FakeWebSocket()

    await app_module.input_socket(ws)

    assert ws.sent == [
        {
            "error": "State backend unavailable",
            "code": "state_backend_unavailable",
            "request_id": "phone-1",
            "session_id": "primary",
            "retryable": True,
        }
    ]


@pytest.mark.asyncio
async def test_event_history_maps_state_backend_outage_to_503(monkeypatch):
    _open_auth(monkeypatch)
    monkeypatch.setattr(app_module.app.state, "bus", OfflineHistoryBus(), raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.event_history("primary", limit=100, after_event_id=None, authorization=None)

    assert exc.value.status_code == 503
    assert exc.value.detail == "State backend unavailable"


@pytest.mark.asyncio
async def test_lifecycle_maps_state_backend_outage_to_503(monkeypatch):
    _open_auth(monkeypatch)
    monkeypatch.setattr(app_module.app.state, "runtime", LifecycleRuntime(), raising=False)
    monkeypatch.setattr(app_module.app.state, "orchestrator", OfflineStateOrchestrator(), raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.reset_session("primary", authorization=None)

    assert exc.value.status_code == 503
    assert exc.value.detail == "State backend unavailable"
