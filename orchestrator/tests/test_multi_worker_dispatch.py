import asyncio

import pytest

from jarvis_orchestrator.management import Task
from jarvis_orchestrator.multi_worker import MultiWorkerDispatcher
from jarvis_orchestrator.worker_registry import InMemoryWorkerRegistry, WorkerNode


class FakeWorker:
    def __init__(self, worker_id: str, result: str, gate: asyncio.Event | None = None):
        self.worker_id = worker_id
        self.result = result
        self.gate = gate
        self.calls: list[tuple[str, str]] = []
        self.started = asyncio.Event()

    async def execute_task(self, task: Task, owner_id: str) -> str:
        self.calls.append((task.task_id, owner_id))
        self.started.set()
        if self.gate is not None:
            await self.gate.wait()
        return self.result


@pytest.mark.asyncio
async def test_two_different_workers_execute_two_tasks_concurrently():
    registry = InMemoryWorkerRegistry()
    await registry.register(WorkerNode("researcher", capabilities=("research",)))
    await registry.register(WorkerNode("analyst", capabilities=("analysis",)))

    release = asyncio.Event()
    researcher = FakeWorker("researcher", "research result", release)
    analyst = FakeWorker("analyst", "analysis result", release)
    dispatcher = MultiWorkerDispatcher(registry, {"researcher": researcher, "analyst": analyst})

    research_task = Task("research", "project-1", "Find options", required_capabilities=("research",))
    analysis_task = Task("analysis", "project-1", "Compare options", required_capabilities=("analysis",))

    pending = asyncio.create_task(
        dispatcher.dispatch_many("owner-a", [research_task, analysis_task])
    )
    await asyncio.wait_for(
        asyncio.gather(researcher.started.wait(), analyst.started.wait()),
        timeout=1,
    )
    assert not pending.done(), "both workers must be active before either is released"
    release.set()

    results = await pending
    assert results["research"].outputs == {"researcher": "research result"}
    assert results["analysis"].outputs == {"analyst": "analysis result"}


@pytest.mark.asyncio
async def test_one_task_can_receive_independent_peer_cross_check_without_duplicate_execution():
    registry = InMemoryWorkerRegistry()
    await registry.register(WorkerNode("researcher", capabilities=("research",)))
    await registry.register(WorkerNode("reviewer", capabilities=("research", "verification")))

    researcher = FakeWorker("researcher", "candidate")
    reviewer = FakeWorker("reviewer", "cross-check")
    dispatcher = MultiWorkerDispatcher(registry, {"researcher": researcher, "reviewer": reviewer})
    task = Task(
        "task-1",
        "project-1",
        "Research and cross-check",
        required_capabilities=("research",),
        assigned_workers=("researcher", "reviewer", "researcher"),
    )

    result = await dispatcher.dispatch("owner-a", task)

    assert result.outputs == {"researcher": "candidate", "reviewer": "cross-check"}
    assert researcher.calls == [("task-1", "owner-a")]
    assert reviewer.calls == [("task-1", "owner-a")]


@pytest.mark.asyncio
async def test_unassigned_task_selects_available_worker_by_capability_not_provider():
    registry = InMemoryWorkerRegistry()
    await registry.register(WorkerNode("worker-b", capabilities=("research",)))
    await registry.register(WorkerNode("worker-a", capabilities=("research",)))
    worker_a = FakeWorker("worker-a", "A")
    worker_b = FakeWorker("worker-b", "B")
    dispatcher = MultiWorkerDispatcher(registry, {"worker-a": worker_a, "worker-b": worker_b})

    task = Task("task-1", "project-1", "Research", required_capabilities=("research",))
    result = await dispatcher.dispatch("owner-a", task)

    assert result.outputs == {"worker-a": "A"}
    assert worker_a.calls == [("task-1", "owner-a")]
    assert worker_b.calls == []
