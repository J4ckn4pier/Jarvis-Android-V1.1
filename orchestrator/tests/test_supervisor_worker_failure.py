from datetime import timedelta

import pytest

from jarvis_orchestrator.management import Project, ProjectState, Task, TaskState
from jarvis_orchestrator.multi_worker import MultiWorkerDispatcher
from jarvis_orchestrator.project_store import InMemoryProjectStore
from jarvis_orchestrator.supervisor import WatchdogSupervisor
from jarvis_orchestrator.worker_registry import InMemoryWorkerRegistry, WorkerNode, WorkerStatus


class FailingWorker:
    def __init__(self, worker_id: str):
        self.worker_id = worker_id
        self.calls = 0

    async def execute_task(self, task: Task, owner_id: str) -> str:
        self.calls += 1
        raise RuntimeError("worker crashed")


class HealthyWorker:
    def __init__(self, worker_id: str):
        self.worker_id = worker_id
        self.calls = 0

    async def execute_task(self, task: Task, owner_id: str) -> str:
        self.calls += 1
        return "recovered result"


@pytest.mark.asyncio
async def test_immediate_worker_failure_is_reassigned_without_waiting_for_stall_timeout():
    store = InMemoryProjectStore()
    registry = InMemoryWorkerRegistry()
    await registry.register(WorkerNode("worker-a", capabilities=("research",)))
    await registry.register(WorkerNode("worker-b", capabilities=("research",)))

    project = Project(
        "project-failure-recovery",
        "owner-a",
        "primary",
        "Research an answer",
        state=ProjectState.ACTIVE,
    )
    task = Task(
        "task-research",
        project.project_id,
        "Research options",
        required_capabilities=("research",),
        assigned_workers=("worker-a",),
        state=TaskState.ASSIGNED,
    )
    await store.save_project(project)
    await store.save_task("owner-a", task)

    failing = FailingWorker("worker-a")
    healthy = HealthyWorker("worker-b")
    supervisor = WatchdogSupervisor(
        store,
        registry,
        MultiWorkerDispatcher(registry, {"worker-a": failing, "worker-b": healthy}),
        timedelta(minutes=5),
    )

    outcome = await supervisor.run_task("owner-a", task)

    assert outcome.task.state is TaskState.COMPLETE
    assert outcome.task.assigned_workers == ("worker-b",)
    assert outcome.outputs == {"worker-b": "recovered result"}
    assert outcome.attempts == 2
    assert outcome.needs_user_escalation is False
    assert failing.calls == 1
    assert healthy.calls == 1
    assert (await registry.get("worker-a")).status is WorkerStatus.DEGRADED

    stored = {item.task_id: item for item in await store.list_tasks("owner-a", project.project_id)}
    assert stored[task.task_id].state is TaskState.COMPLETE
    assert stored[task.task_id].assigned_workers == ("worker-b",)
    event_kinds = [event.kind for event in await store.events("owner-a", project.project_id)]
    assert "task.failed.recovering" in event_kinds
    assert "task.reassigned" in event_kinds
