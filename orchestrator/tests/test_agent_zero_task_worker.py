import json

import httpx
import pytest

from jarvis_orchestrator.management import Task
from jarvis_orchestrator.runtime import (
    AgentZeroRuntime,
    AgentZeroTaskWorker,
    InMemoryAgentContextStore,
)


@pytest.mark.asyncio
async def test_agent_zero_task_worker_runs_management_task_through_supported_open_worker_api():
    payloads = []

    async def handler(request: httpx.Request) -> httpx.Response:
        payloads.append(json.loads(request.read().decode()))
        return httpx.Response(
            200,
            json={"response": "researched result", "context_id": "ctx-management"},
        )

    context_store = InMemoryAgentContextStore()
    client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
        base_url="http://agent-zero:50001",
    )
    runtime = AgentZeroRuntime(
        base_url="http://agent-zero:50001",
        api_key="secret",
        context_store=context_store,
        client=client,
    )
    worker = AgentZeroTaskWorker("research-node", runtime)
    task = Task(
        task_id="research-task",
        project_id="project-1",
        goal="Research three viable options",
        constraints=("open-source only",),
        acceptance_criteria=("three options returned",),
        required_capabilities=("research",),
        assigned_workers=("research-node",),
    )

    output = await worker.execute_task(task, owner_id="owner-a")

    assert output == "researched result"
    assert worker.worker_id == "research-node"
    assert len(payloads) == 1
    assert payloads[0]["message"] == (
        "Goal: Research three viable options\n"
        "Constraints:\n- open-source only\n"
        "Acceptance criteria:\n- three options returned"
    )
    # Management context is stable per owner/project/worker so a provider can
    # continue a long-running project without exposing its context ID to clients.
    assert await context_store.get("owner-a:project-1:research-node") == "ctx-management"
    await client.aclose()
