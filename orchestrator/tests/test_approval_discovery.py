from __future__ import annotations

import pytest

from jarvis_orchestrator.management import Project
from jarvis_orchestrator.management_service import ManagementService
from jarvis_orchestrator.project_store import InMemoryProjectStore


@pytest.mark.asyncio
async def test_pending_approval_id_is_public_durable_and_clears_after_response():
    store = InMemoryProjectStore()
    project = Project("project-1", "owner-a", "primary", "Goal requiring approval")
    await store.save_project(project, event="project.created")
    service = ManagementService(
        store=store,
        registry=object(),
        planning_hook=object(),
        workers={},
    )

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
