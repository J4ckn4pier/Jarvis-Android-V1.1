from __future__ import annotations

from types import SimpleNamespace

import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module
from jarvis_orchestrator.core import BrainEvent


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
async def test_reset_session_calls_runtime(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    runtime = LifecycleRuntime()
    monkeypatch.setattr(app_module.app.state, "runtime", runtime, raising=False)

    result = await app_module.reset_session("primary", authorization=None)

    assert result == {"session_id": "primary", "reset": True}
    assert runtime.reset_calls == ["primary"]


@pytest.mark.asyncio
async def test_terminate_session_calls_runtime(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    runtime = LifecycleRuntime()
    monkeypatch.setattr(app_module.app.state, "runtime", runtime, raising=False)

    result = await app_module.terminate_session("primary", authorization=None)

    assert result == {"session_id": "primary", "terminated": True}
    assert runtime.terminate_calls == ["primary"]


@pytest.mark.asyncio
async def test_lifecycle_endpoint_rejects_runtime_without_capability(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.setattr(app_module.app.state, "runtime", SimpleNamespace(), raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.reset_session("primary", authorization=None)

    assert exc.value.status_code == 501


@pytest.mark.asyncio
async def test_lifecycle_endpoint_reports_missing_worker_session(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    runtime = LifecycleRuntime(reset_result=False)
    monkeypatch.setattr(app_module.app.state, "runtime", runtime, raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.reset_session("missing", authorization=None)

    assert exc.value.status_code == 404


class FakeWebSocket:
    def __init__(self, query_params: dict[str, str]) -> None:
        self.query_params = query_params
        self.accepted = False
        self.closed_code: int | None = None
        self.sent: list[dict[str, object]] = []

    async def accept(self) -> None:
        self.accepted = True

    async def close(self, code: int) -> None:
        self.closed_code = code

    async def send_json(self, payload: dict[str, object]) -> None:
        self.sent.append(payload)


class FiniteEventBus:
    async def subscribe(self):
        yield BrainEvent("other", "t1", "AGENT_OPS", 10, "other", 1, 1.0)
        yield BrainEvent("primary", "t2", "LANGUAGE", 20, "wanted", 2, 2.0)
        raise RuntimeError("end test stream")


@pytest.mark.asyncio
async def test_events_requires_session_subscription(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    ws = FakeWebSocket({})

    await app_module.events(ws)

    assert ws.accepted is False
    assert ws.closed_code == 4400


@pytest.mark.asyncio
async def test_events_filters_out_other_sessions(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.setattr(app_module.app.state, "bus", FiniteEventBus(), raising=False)
    ws = FakeWebSocket({"session_id": "primary"})

    await app_module.events(ws)

    assert ws.accepted is True
    assert [item["session_id"] for item in ws.sent] == ["primary"]
    assert ws.sent[0]["agent_ops_status"] == "wanted"
