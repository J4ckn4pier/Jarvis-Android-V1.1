from __future__ import annotations

from types import SimpleNamespace

import pytest
from fastapi import HTTPException, WebSocketDisconnect

from jarvis_orchestrator import app as app_module
from jarvis_orchestrator.core import BrainEvent
from jarvis_orchestrator.identity import scope_session_id


class RecordingOrchestrator:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str]] = []

    async def submit(self, text: str, session_id: str):
        self.calls.append((text, session_id))
        return {"session_id": session_id, "task_id": "task-1", "response": "ok"}


@pytest.mark.asyncio
async def test_command_scopes_session_to_authenticated_principal(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a","bob":"token-b"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    orchestrator = RecordingOrchestrator()
    monkeypatch.setattr(app_module.app.state, "orchestrator", orchestrator, raising=False)

    result = await app_module.command(
        app_module.Command(text="hello", session_id="primary"),
        authorization="Bearer token-a",
    )

    scoped = scope_session_id("alice", "primary")
    assert orchestrator.calls == [("hello", scoped)]
    assert result["session_id"] == "primary"


class RecordingHistoryBus:
    def __init__(self) -> None:
        self.history_calls: list[tuple[str, int]] = []

    async def history(self, session_id: str, limit: int):
        self.history_calls.append((session_id, limit))
        return [BrainEvent(session_id, "t1", "LANGUAGE", 20, "ready", 1, 1.0)]


@pytest.mark.asyncio
async def test_event_history_is_principal_scoped_and_returns_public_session_id(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a","bob":"token-b"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    bus = RecordingHistoryBus()
    monkeypatch.setattr(app_module.app.state, "bus", bus, raising=False)

    result = await app_module.event_history(
        "primary",
        limit=25,
        authorization="Bearer token-a",
    )

    scoped = scope_session_id("alice", "primary")
    assert bus.history_calls == [(scoped, 25)]
    assert result["session_id"] == "primary"
    assert result["events"][0]["session_id"] == "primary"


class LifecycleRuntime:
    def __init__(self, *, reset_result: bool = True, terminate_result: bool = True) -> None:
        self.reset_result = reset_result
        self.terminate_result = terminate_result
        self.reset_calls: list[str] = []
        self.terminate_calls: list[str] = []

    async def reset(self, session_id: str) -> bool:
        self.reset_calls.append(session_id)
        return self.reset_result

    async def terminate(self, session_id: str) -> bool:
        self.terminate_calls.append(session_id)
        return self.terminate_result


@pytest.mark.asyncio
async def test_reset_session_scopes_runtime_context(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    runtime = LifecycleRuntime()
    monkeypatch.setattr(app_module.app.state, "runtime", runtime, raising=False)

    result = await app_module.reset_session("primary", authorization="Bearer token-a")

    assert result == {"session_id": "primary", "reset": True}
    assert runtime.reset_calls == [scope_session_id("alice", "primary")]


@pytest.mark.asyncio
async def test_terminate_session_scopes_runtime_context(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    runtime = LifecycleRuntime()
    monkeypatch.setattr(app_module.app.state, "runtime", runtime, raising=False)

    result = await app_module.terminate_session("primary", authorization="Bearer token-a")

    assert result == {"session_id": "primary", "terminated": True}
    assert runtime.terminate_calls == [scope_session_id("alice", "primary")]


@pytest.mark.asyncio
async def test_lifecycle_endpoint_rejects_runtime_without_capability(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.setattr(app_module.app.state, "runtime", SimpleNamespace(), raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.reset_session("primary", authorization=None)

    assert exc.value.status_code == 501


@pytest.mark.asyncio
async def test_lifecycle_endpoint_reports_missing_worker_session(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    runtime = LifecycleRuntime(reset_result=False)
    monkeypatch.setattr(app_module.app.state, "runtime", runtime, raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.reset_session("missing", authorization=None)

    assert exc.value.status_code == 404


class FakeWebSocket:
    def __init__(self, query_params: dict[str, str], incoming: list[dict[str, object]] | None = None) -> None:
        self.query_params = query_params
        self.accepted = False
        self.closed_code: int | None = None
        self.sent: list[dict[str, object]] = []
        self.incoming = list(incoming or [])

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


class FiniteEventBus:
    def __init__(self, wanted_session: str) -> None:
        self.wanted_session = wanted_session

    async def subscribe(self):
        yield BrainEvent("someone-else", "t1", "AGENT_OPS", 10, "other", 1, 1.0)
        yield BrainEvent(self.wanted_session, "t2", "LANGUAGE", 20, "wanted", 2, 2.0)
        raise RuntimeError("end test stream")


@pytest.mark.asyncio
async def test_events_requires_session_subscription(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    ws = FakeWebSocket({})

    await app_module.events(ws)

    assert ws.accepted is False
    assert ws.closed_code == 4400


@pytest.mark.asyncio
async def test_events_rejects_oversized_session_id(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    ws = FakeWebSocket({"session_id": "x" * 129})

    await app_module.events(ws)

    assert ws.accepted is False
    assert ws.closed_code == 4400


@pytest.mark.asyncio
async def test_events_scopes_subscription_to_authenticated_principal(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a","bob":"token-b"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    scoped = scope_session_id("alice", "primary")
    monkeypatch.setattr(app_module.app.state, "bus", FiniteEventBus(scoped), raising=False)
    ws = FakeWebSocket({"token": "token-a", "session_id": "primary"})

    await app_module.events(ws)

    assert ws.accepted is True
    assert [item["session_id"] for item in ws.sent] == ["primary"]
    assert ws.sent[0]["agent_ops_status"] == "wanted"


@pytest.mark.asyncio
async def test_input_socket_scopes_session_and_returns_public_id(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    orchestrator = RecordingOrchestrator()
    monkeypatch.setattr(app_module.app.state, "orchestrator", orchestrator, raising=False)
    ws = FakeWebSocket(
        {"token": "token-a"},
        incoming=[{"text": "hello", "session_id": "primary"}],
    )

    await app_module.input_socket(ws)

    assert orchestrator.calls == [("hello", scope_session_id("alice", "primary"))]
    assert ws.sent[0]["session_id"] == "primary"
    assert ws.sent[0]["response"] == "ok"


@pytest.mark.asyncio
async def test_input_socket_rejects_oversized_text_without_dispatch(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    orchestrator = RecordingOrchestrator()
    monkeypatch.setattr(app_module.app.state, "orchestrator", orchestrator, raising=False)
    ws = FakeWebSocket({}, incoming=[{"text": "x" * 100_001, "session_id": "primary"}])

    await app_module.input_socket(ws)

    assert orchestrator.calls == []
    assert ws.sent == [{"error": "text must be between 1 and 100000 characters"}]


@pytest.mark.asyncio
async def test_input_socket_rejects_oversized_session_without_dispatch(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    orchestrator = RecordingOrchestrator()
    monkeypatch.setattr(app_module.app.state, "orchestrator", orchestrator, raising=False)
    ws = FakeWebSocket({}, incoming=[{"text": "hello", "session_id": "s" * 129}])

    await app_module.input_socket(ws)

    assert orchestrator.calls == []
    assert ws.sent == [{"error": "session_id must be between 1 and 128 characters"}]
