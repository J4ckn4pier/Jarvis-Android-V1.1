from __future__ import annotations

from dataclasses import replace

import pytest

from jarvis_orchestrator.management import Project, ProjectState, Task, TaskState
from jarvis_orchestrator.management_service import ManagementService
from jarvis_orchestrator.project_store import InMemoryProjectStore


@pytest.mark.asyncio
async def test_morning_summary_reconstructs_progress_recovery_and_result_after_service_restart():
    store = InMemoryProjectStore()
    project = Project(
        project_id="project-briefing",
        owner_id="owner-a",
        session_id="primary",
        goal="Find a good restaurant for dinner",
        state=ProjectState.COMPLETE,
    )
    await store.save_project(project, event="project.complete")

    research = Task(
        task_id="task-research",
        project_id=project.project_id,
        goal="Research restaurants",
        assigned_workers=("research-a",),
        state=TaskState.RUNNING,
    )
    await store.save_task("owner-a", research, event="task.running")
    recovered = replace(
        research,
        assigned_workers=("research-b",),
        state=TaskState.ASSIGNED,
    )
    await store.save_task("owner-a", recovered, event="task.reassigned")
    await store.save_task(
        "owner-a",
        replace(recovered, state=TaskState.COMPLETE),
        event="task.complete",
    )

    synthesis = Task(
        task_id="task-synthesis",
        project_id=project.project_id,
        goal="Synthesize recommendation",
        state=TaskState.COMPLETE,
    )
    await store.save_task("owner-a", synthesis, event="task.complete")
    await store.save_task_output(
        "owner-a",
        project.project_id,
        synthesis.task_id,
        "Choose Thai Garden; it fits the budget and passed verification.",
    )

    # A fresh service object emulates JARVIS waking up later and rebuilding the
    # briefing from persistent project state rather than process-local memory.
    restarted = ManagementService(
        store=store,
        registry=object(),
        planning_hook=object(),
        workers={},
    )
    summary = await restarted.morning_summary("owner-a", project.project_id)

    assert summary["project_id"] == project.project_id
    assert summary["state"] == "complete"
    assert summary["completed_tasks"] == 2
    assert summary["total_tasks"] == 2
    assert summary["recoveries"] == 1
    assert summary["pending_approvals"] == 0
    assert "Find a good restaurant for dinner" in summary["briefing"]
    assert "2/2" in summary["briefing"]
    assert "recovered 1 stalled task" in summary["briefing"]
    assert "Choose Thai Garden" in summary["briefing"]
    assert "research-a" not in summary["briefing"]
    assert "research-b" not in summary["briefing"]
    assert summary["provider_details_exposed"] is False
