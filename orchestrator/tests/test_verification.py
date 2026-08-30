import pytest

from jarvis_orchestrator.management import Project, ProjectState, Task, TaskState
from jarvis_orchestrator.project_store import InMemoryProjectStore
from jarvis_orchestrator.verification import (
    CompletionGate,
    EvidenceArtifact,
    EvidenceKind,
    InMemoryEvidenceStore,
)


async def _setup_project():
    store = InMemoryProjectStore()
    evidence = InMemoryEvidenceStore()
    project = Project(
        "project-1",
        "owner-a",
        "primary",
        "Research and verify",
        acceptance_criteria=("recommendation independently verified",),
        state=ProjectState.VERIFYING,
    )
    task = Task(
        "task-1",
        project.project_id,
        "Research",
        acceptance_criteria=("sources checked",),
        state=TaskState.COMPLETE,
    )
    await store.save_project(project)
    await store.save_task("owner-a", task)
    return store, evidence, project, task


@pytest.mark.asyncio
async def test_worker_done_assertion_alone_cannot_complete_project():
    store, evidence, project, task = await _setup_project()
    gate = CompletionGate(store, evidence)
    await evidence.record(
        "owner-a",
        EvidenceArtifact(
            evidence_id="e-assert",
            project_id=project.project_id,
            task_id=task.task_id,
            criterion="recommendation independently verified",
            kind=EvidenceKind.WORKER_ASSERTION,
            passed=True,
            detail="done",
            source_worker_id="worker-a",
        ),
    )

    outcome = await gate.try_complete("owner-a", project.project_id)

    assert outcome.accepted is False
    assert any("machine-verifiable or verifier evidence" in reason for reason in outcome.reasons)
    assert (await store.get_project("owner-a", project.project_id)).state is ProjectState.VERIFYING


@pytest.mark.asyncio
async def test_project_completes_only_when_all_tasks_and_acceptance_evidence_pass():
    store, evidence, project, task = await _setup_project()
    gate = CompletionGate(store, evidence)

    await evidence.record(
        "owner-a",
        EvidenceArtifact(
            "e-task",
            project.project_id,
            task.task_id,
            "sources checked",
            EvidenceKind.MACHINE_CHECK,
            True,
            "three sources present",
            "verifier",
        ),
    )
    await evidence.record(
        "owner-a",
        EvidenceArtifact(
            "e-project",
            project.project_id,
            task.task_id,
            "recommendation independently verified",
            EvidenceKind.VERIFIER_RESULT,
            True,
            "independent cross-check passed",
            "worker-verifier",
        ),
    )

    outcome = await gate.try_complete("owner-a", project.project_id)

    assert outcome.accepted is True
    assert set(outcome.evidence_ids) == {"e-task", "e-project"}
    assert (await store.get_project("owner-a", project.project_id)).state is ProjectState.COMPLETE


@pytest.mark.asyncio
async def test_failed_evidence_or_unfinished_required_task_rejects_completion():
    store, evidence, project, task = await _setup_project()
    unfinished = Task("task-2", project.project_id, "Compare", state=TaskState.RUNNING)
    await store.save_task("owner-a", unfinished)
    gate = CompletionGate(store, evidence)
    await evidence.record(
        "owner-a",
        EvidenceArtifact(
            "e-fail",
            project.project_id,
            task.task_id,
            "recommendation independently verified",
            EvidenceKind.VERIFIER_RESULT,
            False,
            "cross-check disagreed",
            "worker-verifier",
        ),
    )

    outcome = await gate.try_complete("owner-a", project.project_id)

    assert outcome.accepted is False
    assert any("unfinished task" in reason for reason in outcome.reasons)
    assert any("failed evidence" in reason for reason in outcome.reasons)
    assert (await store.get_project("owner-a", project.project_id)).state is ProjectState.VERIFYING


@pytest.mark.asyncio
async def test_evidence_is_owner_isolated():
    evidence = InMemoryEvidenceStore()
    artifact = EvidenceArtifact(
        "e-1",
        "project-1",
        "task-1",
        "criterion",
        EvidenceKind.MACHINE_CHECK,
        True,
        "ok",
        "verifier",
    )
    await evidence.record("owner-a", artifact)

    assert await evidence.list_for_project("owner-a", "project-1") == [artifact]
    assert await evidence.list_for_project("owner-b", "project-1") == []
