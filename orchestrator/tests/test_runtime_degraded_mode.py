import httpx
import pytest

from jarvis_orchestrator.runtime import AgentZeroRuntime, InMemoryAgentContextStore


@pytest.mark.asyncio
async def test_agent_zero_transport_failure_becomes_stable_runtime_error():
    async def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("worker refused connection", request=request)

    client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
        base_url="http://agent-zero:50001",
    )
    runtime = AgentZeroRuntime(
        base_url="http://agent-zero:50001",
        api_key="secret",
        context_store=InMemoryAgentContextStore(),
        client=client,
    )

    with pytest.raises(RuntimeError, match="Agent Zero unavailable"):
        await runtime.execute("work", "session-a")

    await client.aclose()


@pytest.mark.asyncio
async def test_agent_zero_server_failure_becomes_stable_runtime_error():
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503, json={"error": "worker overloaded"})

    client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
        base_url="http://agent-zero:50001",
    )
    runtime = AgentZeroRuntime(
        base_url="http://agent-zero:50001",
        api_key="secret",
        context_store=InMemoryAgentContextStore(),
        client=client,
    )

    with pytest.raises(RuntimeError, match="Agent Zero unavailable"):
        await runtime.execute("work", "session-a")

    await client.aclose()
