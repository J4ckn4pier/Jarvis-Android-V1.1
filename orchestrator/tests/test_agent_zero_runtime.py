import json

import httpx
import pytest

from jarvis_orchestrator.runtime import (
    AgentZeroRuntime,
    InMemoryAgentContextStore,
    WorkerUnavailableError,
    build_runtime,
)


@pytest.mark.asyncio
async def test_agent_zero_first_message_creates_and_remembers_context():
    requests = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json={"response": "worker answer", "context_id": "ctx-123"})

    store = InMemoryAgentContextStore()
    client = httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://agent-zero:50001")
    runtime = AgentZeroRuntime(
        base_url="http://agent-zero:50001",
        api_key="secret",
        context_store=store,
        client=client,
    )

    result = await runtime.execute("do the work", "session-a")

    assert result == "worker answer"
    assert await store.get("session-a") == "ctx-123"
    assert len(requests) == 1
    request = requests[0]
    assert request.url.path == "/api/api_message"
    assert request.headers["X-API-KEY"] == "secret"
    assert request.read().decode() == '{"message":"do the work","lifetime_hours":24}'
    await client.aclose()


@pytest.mark.asyncio
async def test_agent_zero_reuses_context_for_same_jarvis_session():
    payloads = []

    async def handler(request: httpx.Request) -> httpx.Response:
        payloads.append(request.read().decode())
        return httpx.Response(200, json={"response": "continued", "context_id": "ctx-123"})

    store = InMemoryAgentContextStore()
    await store.set("session-a", "ctx-123")
    client = httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://agent-zero:50001")
    runtime = AgentZeroRuntime(
        base_url="http://agent-zero:50001",
        api_key="secret",
        context_store=store,
        client=client,
    )

    result = await runtime.execute("continue", "session-a")

    assert result == "continued"
    assert payloads == ['{"message":"continue","lifetime_hours":24,"context_id":"ctx-123"}']
    await client.aclose()


@pytest.mark.asyncio
async def test_agent_zero_retries_once_with_fresh_context_when_saved_context_expired():
    payloads = []

    async def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.read().decode())
        payloads.append(payload)
        if len(payloads) == 1:
            return httpx.Response(404, json={"error": "Context not found"})
        return httpx.Response(200, json={"response": "fresh answer", "context_id": "ctx-new"})

    store = InMemoryAgentContextStore()
    await store.set("session-a", "ctx-expired")
    client = httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://agent-zero:50001")
    runtime = AgentZeroRuntime(
        base_url="http://agent-zero:50001",
        api_key="secret",
        context_store=store,
        client=client,
    )

    result = await runtime.execute("continue after expiry", "session-a")

    assert result == "fresh answer"
    assert payloads == [
        {"message": "continue after expiry", "lifetime_hours": 24, "context_id": "ctx-expired"},
        {"message": "continue after expiry", "lifetime_hours": 24},
    ]
    assert await store.get("session-a") == "ctx-new"
    await client.aclose()


@pytest.mark.asyncio
async def test_agent_zero_surfaces_server_failure_as_worker_unavailable_without_retry():
    calls = 0

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        return httpx.Response(503, json={"error": "offline"})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://agent-zero:50001")
    runtime = AgentZeroRuntime(
        base_url="http://agent-zero:50001",
        api_key="secret",
        context_store=InMemoryAgentContextStore(),
        client=client,
    )

    with pytest.raises(WorkerUnavailableError, match="Agent Zero unavailable"):
        await runtime.execute("work", "session-a")
    assert calls == 1
    await client.aclose()


@pytest.mark.asyncio
async def test_agent_zero_readiness_uses_supported_health_endpoint():
    requests = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json={"status": "ok"})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://agent-zero:50001")
    runtime = AgentZeroRuntime(
        base_url="http://agent-zero:50001",
        api_key="secret",
        context_store=InMemoryAgentContextStore(),
        client=client,
    )

    assert await runtime.check_ready() is True
    assert [request.url.path for request in requests] == ["/api/health"]
    await client.aclose()


@pytest.mark.asyncio
async def test_agent_zero_readiness_uses_short_timeout_instead_of_inference_timeout():
    requests = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json={"status": "ok"})

    client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
        base_url="http://agent-zero:50001",
        timeout=300.0,
    )
    runtime = AgentZeroRuntime(
        base_url="http://agent-zero:50001",
        api_key="secret",
        context_store=InMemoryAgentContextStore(),
        client=client,
    )

    assert await runtime.check_ready() is True
    assert requests[0].extensions["timeout"]["read"] == 5.0
    await client.aclose()


@pytest.mark.asyncio
async def test_agent_zero_reset_keeps_mapping_and_calls_supported_endpoint():
    requests = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append((request.url.path, json.loads(request.read().decode())))
        return httpx.Response(200, json={"success": True, "context_id": "ctx-123"})

    store = InMemoryAgentContextStore()
    await store.set("session-a", "ctx-123")
    client = httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://agent-zero:50001")
    runtime = AgentZeroRuntime("http://agent-zero:50001", "secret", store, client=client)

    assert await runtime.reset("session-a") is True
    assert requests == [("/api/api_reset_chat", {"context_id": "ctx-123"})]
    assert await store.get("session-a") == "ctx-123"
    await client.aclose()


@pytest.mark.asyncio
async def test_agent_zero_terminate_clears_mapping_after_success():
    requests = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append((request.url.path, json.loads(request.read().decode())))
        return httpx.Response(200, json={"success": True, "context_id": "ctx-123"})

    store = InMemoryAgentContextStore()
    await store.set("session-a", "ctx-123")
    client = httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://agent-zero:50001")
    runtime = AgentZeroRuntime("http://agent-zero:50001", "secret", store, client=client)

    assert await runtime.terminate("session-a") is True
    assert requests == [("/api/api_terminate_chat", {"context_id": "ctx-123"})]
    assert await store.get("session-a") is None
    await client.aclose()


def test_runtime_factory_defaults_to_echo(monkeypatch):
    monkeypatch.delenv("JARVIS_RUNTIME", raising=False)
    runtime = build_runtime(context_store=InMemoryAgentContextStore())
    assert runtime.__class__.__name__ == "EchoRuntime"


def test_runtime_factory_requires_agent_zero_configuration(monkeypatch):
    monkeypatch.setenv("JARVIS_RUNTIME", "agent-zero")
    monkeypatch.delenv("AGENT_ZERO_URL", raising=False)
    monkeypatch.delenv("AGENT_ZERO_API_KEY", raising=False)

    with pytest.raises(RuntimeError, match="AGENT_ZERO_URL"):
        build_runtime(context_store=InMemoryAgentContextStore())
