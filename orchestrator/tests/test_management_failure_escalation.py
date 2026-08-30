from __future__ import annotations

import pytest

from jarvis_orchestrator.management import Project, ProjectState, Task, TaskState
from jarvis_orchestrator.management_service import ManagementService
from jarvis_orchestrator.project_store import InMemoryProjectStore
from jarvis_orchestrator.worker_registry import InMemoryWorkerRegistry, WorkerNode


class FailingWorker:
    worker_id = "worker-a"

    async def execute_task(self, task: Task, owner_id: str) -> str:
        raise RuntimeError("worker crashed")


@pytest.mark.asyncio
async def test_run_ready_surfaces_pending_approval_when_failure_has_no_replacement():
    store = InMemoryProjectStore()
    registry = InMemoryWorkerRegistry()
    await registry.register(WorkerNode("worker-a", capabilities=("research",)))

    project = Project(
        "project-escalation",
        "owner-a",
        "primary",
        "Research an answer",
        state=ProjectState.ACTIVE,
    )
    task = Task(
        "task-research",
        project.project_id,
        "Research",
        required_capabilities=("research",),
        assigned_workers=("worker-a",),
        state=TaskState.ASSIGNED,
    )
    await store.save_project(project)
    await store.save_task("owner-a", task)

    service = ManagementService(
        store=store,
        registry=registry,
        planning_hook=object(),
        workers={"worker-a": FailingWorker()},
    )

    await service.run_ready("owner-a", project.project_id)
    status = await service.status("owner-a", project.project_id)

    assert status["task_states"] == {"blocked": 1}
    assert len(status["pending_approvals"]) == 1
    approval = status["pending_approvals"][0]
    assert approval["task_id"] == task.task_id
    assert approval["approval_id"].startswith("approval-")

    public_events = await service.events("owner-a", project.project_id, None, 100)
    requested = [e for e in public_events["events"] if e["kind"] == "approval.requested"]
    assert len(requested) == 1
    assert requested[0]["task_id"] == task.task_id
    assert requested[0]["approval_id"] == approval["approval_id"]
