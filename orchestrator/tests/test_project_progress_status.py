from datetime import datetime, timedelta, timezone

import pytest

from jarvis_orchestrator.management import Project, ProjectState, Task, TaskState
from jarvis_orchestrator.management_service import ManagementService
from jarvis_orchestrator.project_store import InMemoryProjectStore
from jarvis_orchestrator.worker_registry import InMemoryWorkerRegistry, WorkerNode


class Worker:
    worker_id = "worker-a"

    async def execute_task(self, task: Task, owner_id: str) -> str:
        return "done"


@pytest.mark.asyncio
async def test_public_project_progress_advances_when_a_task_completes():
    old = datetime(2026, 8, 30, 20, 0, tzinfo=timezone.utc)
    now = old + timedelta(minutes=15)
    store = InMemoryProjectStore()
    registry = InMemoryWorkerRegistry()
    await registry.register(WorkerNode("worker-a", capabilities=("research",)))

    project = Project(
        "project-progress",
        "owner-a",
        "primary",
        "Research an answer",
        state=ProjectState.ACTIVE,
        created_at=old,
        updated_at=old,
        last_progress_at=old,
    )
    task = Task(
        "task-research",
        project.project_id,
        "Research",
        required_capabilities=("research",),
        assigned_workers=("worker-a",),
        state=TaskState.ASSIGNED,
        created_at=old,
        updated_at=old,
        last_progress_at=old,
    )
    await store.save_project(project)
    await store.save_task("owner-a", task)

    service = ManagementService(
        store=store,
        registry=registry,
        planning_hook=object(),
        workers={"worker-a": Worker()},
        clock=lambda: now,
    )

    await service.run_ready("owner-a", project.project_id)
    status = await service.status("owner-a", project.project_id)

    assert status["task_states"] == {"complete": 1}
    assert status["last_progress_at"] == now.isoformat()
    stored = await store.get_project("owner-a", project.project_id)
    assert stored is not None
    assert stored.last_progress_at == now
