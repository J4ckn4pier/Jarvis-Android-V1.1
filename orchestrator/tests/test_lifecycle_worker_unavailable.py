from __future__ import annotations

import httpx
import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module
from jarvis_orchestrator.runtime import (
    AgentZeroRuntime,
    InMemoryAgentContextStore,
    WorkerUnavailableError,
)


@pytest.mark.asyncio
@pytest.mark.parametrize("operation", ["reset", "terminate"])
async def test_agent_zero_lifecycle_server_failure_is_worker_unavailable(operation):
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503, json={"error": "offline"})

    store = InMemoryAgentContextStore()
    await store.set("session-a", "ctx-123")
    client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
        base_url="http://agent-zero:50001",
    )
    runtime = AgentZeroRuntime("http://agent-zero:50001", "secret", store, client=client)

    with pytest.raises(WorkerUnavailableError, match="Agent Zero unavailable"):
        await getattr(runtime, operation)("session-a")

    await client.aclose()


class UnavailableLifecycleRuntime:
    async def reset(self, session_id: str) -> bool:
        raise WorkerUnavailableError("Agent Zero unavailable")


class LifecycleSerializer:
    async def run_session_operation(self, session_id: str, operation):
        return await operation()


@pytest.mark.asyncio
async def test_lifecycle_endpoint_returns_503_when_worker_is_unavailable(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.setattr(app_module.app.state, "runtime", UnavailableLifecycleRuntime(), raising=False)
    monkeypatch.setattr(app_module.app.state, "orchestrator", LifecycleSerializer(), raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.reset_session("primary", authorization=None)

    assert exc.value.status_code == 503
    assert exc.value.detail == "Worker runtime unavailable"
