import pytest

from jarvis_orchestrator.management import Project, ProjectState, Task, TaskState
from jarvis_orchestrator.management_service import ManagementService
from jarvis_orchestrator.project_store import InMemoryProjectStore
from jarvis_orchestrator.verification import EvidenceKind


def _service(store):
    return ManagementService(
        store=store,
        registry=object(),
        planning_hook=object(),
        workers={},
    )


@pytest.mark.asyncio
async def test_verification_evidence_survives_management_service_restart():
    store = InMemoryProjectStore()
    project = Project(
        "project-restart-verify",
        "owner-a",
        "primary",
        "Research and independently verify a recommendation",
        acceptance_criteria=("recommendation independently verified",),
        state=ProjectState.VERIFYING,
    )
    task = Task(
        "task-research",
        project.project_id,
        "Research recommendation",
        acceptance_criteria=("sources checked",),
        state=TaskState.COMPLETE,
    )
    await store.save_project(project)
    await store.save_task("owner-a", task)

    before_restart = _service(store)
    await before_restart.record_evidence(
        "owner-a",
        project.project_id,
        task.task_id,
        "sources checked",
        EvidenceKind.MACHINE_CHECK,
        True,
        "three independent sources present",
        "verifier-a",
    )
    await before_restart.record_evidence(
        "owner-a",
        project.project_id,
        task.task_id,
        "recommendation independently verified",
        EvidenceKind.VERIFIER_RESULT,
        True,
        "independent cross-check passed",
        "verifier-b",
    )

    # Recreating the service must not erase proof already recorded for a
    # long-running project. Production uses the durable Valkey ProjectStore.
    after_restart = _service(store)
    outcome = await after_restart.verify_and_complete("owner-a", project.project_id)

    assert outcome["accepted"] is True
    assert outcome["state"] == "complete"
    assert len(outcome["evidence_ids"]) == 2
