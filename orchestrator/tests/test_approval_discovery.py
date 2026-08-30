from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest

from jarvis_orchestrator.management import Project, ProjectState, Task, TaskState
from jarvis_orchestrator.management_service import ManagementService
from jarvis_orchestrator.project_store import InMemoryProjectStore
from jarvis_orchestrator.worker_registry import InMemoryWorkerRegistry, WorkerNode, WorkerStatus


def _service(store, *, registry=None, clock=None):
    return ManagementService(
        store=store,
        registry=registry or object(),
        planning_hook=object(),
        workers={},
        clock=clock or (lambda: datetime.now(timezone.utc)),
    )


@pytest.mark.asyncio
async def test_pending_approval_id_is_public_durable_and_clears_after_response():
    store = InMemoryProjectStore()
    project = Project("project-1", "owner-a", "primary", "Goal requiring approval")
    await store.save_project(project, event="project.created")
    service = _service(store)

    requested = await service.request_approval(
        "owner-a",
        "project-1",
        "approval-7",
        task_id="task-1",
    )
    page = await service.events("owner-a", "project-1", None, 100)
    status = await service.status("owner-a", "project-1")

    assert requested == {
        "project_id": "project-1",
        "approval_id": "approval-7",
        "task_id": "task-1",
        "state": "pending",
    }
    approval_event = page["events"][-1]
    assert approval_event["kind"] == "approval.requested"
    assert approval_event["approval_id"] == "approval-7"
    assert approval_event["task_id"] == "task-1"
    assert "worker" not in str(approval_event).lower()
    assert "provider" not in str(approval_event).lower()
    assert status["pending_approvals"] == [
        {"approval_id": "approval-7", "task_id": "task-1"}
    ]

    response = await service.approve(
        "owner-a",
        "project-1",
        "approval-7",
        True,
        "Proceed",
    )
    final_status = await service.status("owner-a", "project-1")
    final_page = await service.events("owner-a", "project-1", None, 100)

    assert response["approval_id"] == "approval-7"
    assert final_status["pending_approvals"] == []
    assert final_page["events"][-1]["approval_id"] == "approval-7"
    assert final_page["events"][-1]["kind"] == "approval.approved"


@pytest.mark.asyncio
async def test_unknown_or_already_consumed_approval_id_is_rejected():
    store = InMemoryProjectStore()
    project = Project("project-1", "owner-a", "primary", "Goal requiring approval")
    await store.save_project(project, event="project.created")
    service = _service(store)

    with pytest.raises(KeyError, match="approval-missing"):
        await service.approve(
            "owner-a",
            "project-1",
            "approval-missing",
            True,
            None,
        )

    await service.request_approval(
        "owner-a",
        "project-1",
        "approval-7",
        task_id="task-1",
    )
    await service.approve("owner-a", "project-1", "approval-7", True, "Proceed")

    with pytest.raises(KeyError, match="approval-7"):
        await service.approve(
            "owner-a",
            "project-1",
            "approval-7",
            True,
            "duplicate retry",
        )


@pytest.mark.asyncio
async def test_watchdog_user_escalation_creates_discoverable_opaque_approval():
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
    project = Project(
        "project-1",
        "owner-a",
        "primary",
        "Long-running research",
        state=ProjectState.ACTIVE,
    )
    task = Task(
        "task-1",
        project.project_id,
        "Research",
        required_capabilities=("research",),
        assigned_workers=("only-worker",),
        state=TaskState.RUNNING,
        created_at=old,
        updated_at=old,
        last_progress_at=old,
    )
    await store.save_project(project)
    await store.save_task("owner-a", task)
    service = _service(store, registry=registry, clock=lambda: now)

    recovered = await service.recover_stalled("owner-a", project.project_id)
    status = await service.status("owner-a", project.project_id)
    page = await service.events("owner-a", project.project_id, None, 100)

    assert recovered == {"reassigned": [], "escalated": ["task-1"]}
    assert len(status["pending_approvals"]) == 1
    pending = status["pending_approvals"][0]
    assert pending["task_id"] == "task-1"
    assert pending["approval_id"] != "task-1"
    assert pending["approval_id"]
    assert page["events"][-1]["kind"] == "approval.requested"
    assert page["events"][-1]["approval_id"] == pending["approval_id"]
