from dataclasses import replace
from datetime import datetime, timedelta, timezone

import pytest

from jarvis_orchestrator.management import Project, ProjectState, Task, TaskState
from jarvis_orchestrator.multi_worker import MultiWorkerDispatcher
from jarvis_orchestrator.project_store import InMemoryProjectStore
from jarvis_orchestrator.supervisor import WatchdogSupervisor
from jarvis_orchestrator.worker_registry import InMemoryWorkerRegistry, WorkerNode, WorkerStatus


class FakeWorker:
    def __init__(self, worker_id: str, result: str):
        self.worker_id = worker_id
        self.result = result
        self.calls = 0

    async def execute_task(self, task: Task, owner_id: str) -> str:
        self.calls += 1
        return self.result


@pytest.mark.asyncio
async def test_stalled_worker_is_detected_and_reassigned_without_duplicate_completed_side_effects():
    now = datetime(2026, 8, 30, 20, 0, tzinfo=timezone.utc)
    old = now - timedelta(minutes=10)
    store = InMemoryProjectStore()
    registry = InMemoryWorkerRegistry()
    await registry.register(
        WorkerNode(
            "worker-a",
            capabilities=("research",),
            status=WorkerStatus.BUSY,
            registered_at=old,
            updated_at=old,
            last_heartbeat_at=old,
            last_progress_at=old,
        )
    )
    await registry.register(WorkerNode("worker-b", capabilities=("research",)))

    project = Project("project-1", "owner-a", "primary", "Research", state=ProjectState.ACTIVE)
    task = Task(
        "task-1",
        project.project_id,
        "Research options",
        required_capabilities=("research",),
        assigned_workers=("worker-a",),
        state=TaskState.RUNNING,
        created_at=old,
        updated_at=old,
        last_progress_at=old,
    )
    await store.save_project(project)
    await store.save_task("owner-a", task)

    worker_a = FakeWorker("worker-a", "should not run again")
    worker_b = FakeWorker("worker-b", "recovered result")
    dispatcher = MultiWorkerDispatcher(registry, {"worker-a": worker_a, "worker-b": worker_b})
    supervisor = WatchdogSupervisor(
        store=store,
        registry=registry,
        dispatcher=dispatcher,
        stall_after=timedelta(minutes=5),
        clock=lambda: now,
    )

    recovered = await supervisor.recover_stalled("owner-a", task)

    assert recovered.task.state is TaskState.COMPLETE
    assert recovered.task.assigned_workers == ("worker-b",)
    assert recovered.outputs == {"worker-b": "recovered result"}
    assert worker_a.calls == 0
    assert worker_b.calls == 1
    assert recovered.attempts == 2

    # A second watchdog pass over already completed work must not repeat the side effect.
    again = await supervisor.recover_stalled("owner-a", recovered.task)
    assert again.task is recovered.task
    assert worker_b.calls == 1
    assert again.attempts == 2


@pytest.mark.asyncio
async def test_watchdog_escalates_only_when_no_compatible_autonomous_route_remains():
    now = datetime(2026, 8, 30, 20, 0, tzinfo=timezone.utc)
    old = now - timedelta(minutes=10)
    store = InMemoryProjectStore()
    registry = InMemoryWorkerRegistry()
    await registry.register(
        WorkerNode(
            "only-worker",
            capabilities=("research",),
            status=WorkerStatus.BUSY,
            registered_at=old,
            updated_at=old,
            last_heartbeat_at=old,
            last_progress_at=old,
        )
    )
    dispatcher = MultiWorkerDispatcher(registry, {})
    supervisor = WatchdogSupervisor(store, registry, dispatcher, timedelta(minutes=5), clock=lambda: now)
    task = Task(
        "task-1",
        "project-1",
        "Research",
        required_capabilities=("research",),
        assigned_workers=("only-worker",),
        state=TaskState.RUNNING,
        created_at=old,
        updated_at=old,
        last_progress_at=old,
    )

    outcome = await supervisor.recover_stalled("owner-a", task)

    assert outcome.task.state is TaskState.BLOCKED
    assert outcome.blocker == "no compatible available worker"
    assert outcome.needs_user_escalation is True


@pytest.mark.asyncio
async def test_user_cancel_marks_project_and_unfinished_tasks_cancelled_and_prevents_dispatch():
    store = InMemoryProjectStore()
    registry = InMemoryWorkerRegistry()
    await registry.register(WorkerNode("worker-a", capabilities=("research",)))
    worker = FakeWorker("worker-a", "must not run")
    supervisor = WatchdogSupervisor(
        store,
        registry,
        MultiWorkerDispatcher(registry, {"worker-a": worker}),
        timedelta(minutes=5),
    )
    project = Project("project-1", "owner-a", "primary", "Research", state=ProjectState.ACTIVE)
    task = Task("task-1", project.project_id, "Research", required_capabilities=("research",))
    done = replace(task, task_id="task-done", state=TaskState.COMPLETE)
    await store.save_project(project)
    await store.save_task("owner-a", task)
    await store.save_task("owner-a", done)

    cancelled = await supervisor.cancel_project("owner-a", project.project_id)

    assert cancelled.state is ProjectState.CANCELLED
    tasks = {t.task_id: t for t in await store.list_tasks("owner-a", project.project_id)}
    assert tasks["task-1"].state is TaskState.CANCELLED
    assert tasks["task-done"].state is TaskState.COMPLETE

    outcome = await supervisor.run_task("owner-a", tasks["task-1"])
    assert outcome.task.state is TaskState.CANCELLED
    assert worker.calls == 0
