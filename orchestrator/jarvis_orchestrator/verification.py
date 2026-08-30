"""Evidence-backed verification and completion gating for managed JARVIS work."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Protocol

from .management import ProjectState, TaskState
from .project_store import ProjectStore


def _now() -> datetime:
    return datetime.now(timezone.utc)


class EvidenceKind(str, Enum):
    """Trust class for evidence produced while executing a task graph."""

    WORKER_ASSERTION = "worker_assertion"
    MACHINE_CHECK = "machine_check"
    VERIFIER_RESULT = "verifier_result"
    ARTIFACT = "artifact"


@dataclass(frozen=True, slots=True)
class EvidenceArtifact:
    evidence_id: str
    project_id: str
    task_id: str | None
    criterion: str
    kind: EvidenceKind
    passed: bool
    detail: str
    source_worker_id: str | None = None
    created_at: datetime = field(default_factory=_now)


class EvidenceStore(Protocol):
    async def record(self, owner_id: str, artifact: EvidenceArtifact) -> None: ...
    async def list_for_project(self, owner_id: str, project_id: str) -> list[EvidenceArtifact]: ...


class InMemoryEvidenceStore:
    """Owner-isolated deterministic evidence store for focused unit tests."""

    def __init__(self) -> None:
        self._artifacts: dict[tuple[str, str, str], EvidenceArtifact] = {}

    async def record(self, owner_id: str, artifact: EvidenceArtifact) -> None:
        self._artifacts[(owner_id, artifact.project_id, artifact.evidence_id)] = artifact

    async def list_for_project(self, owner_id: str, project_id: str) -> list[EvidenceArtifact]:
        return sorted(
            (
                artifact
                for (owner, project, _), artifact in self._artifacts.items()
                if owner == owner_id and project == project_id
            ),
            key=lambda artifact: artifact.evidence_id,
        )


class ProjectEvidenceStore:
    """Persist verifier proof through the same owner-isolated ProjectStore as work state."""

    def __init__(self, store: ProjectStore) -> None:
        self.store = store

    async def record(self, owner_id: str, artifact: EvidenceArtifact) -> None:
        payload = json.dumps(
            {
                "evidence_id": artifact.evidence_id,
                "project_id": artifact.project_id,
                "task_id": artifact.task_id,
                "criterion": artifact.criterion,
                "kind": artifact.kind.value,
                "passed": artifact.passed,
                "detail": artifact.detail,
                "source_worker_id": artifact.source_worker_id,
                "created_at": artifact.created_at.isoformat(),
            },
            separators=(",", ":"),
            sort_keys=True,
        )
        await self.store.save_verification_evidence(
            owner_id,
            artifact.project_id,
            artifact.evidence_id,
            payload,
        )

    async def list_for_project(self, owner_id: str, project_id: str) -> list[EvidenceArtifact]:
        stored = await self.store.verification_evidence(owner_id, project_id)
        artifacts: list[EvidenceArtifact] = []
        for evidence_id in sorted(stored):
            payload = json.loads(stored[evidence_id])
            artifacts.append(
                EvidenceArtifact(
                    evidence_id=payload["evidence_id"],
                    project_id=payload["project_id"],
                    task_id=payload["task_id"],
                    criterion=payload["criterion"],
                    kind=EvidenceKind(payload["kind"]),
                    passed=payload["passed"],
                    detail=payload["detail"],
                    source_worker_id=payload["source_worker_id"],
                    created_at=datetime.fromisoformat(payload["created_at"]),
                )
            )
        return artifacts


@dataclass(frozen=True, slots=True)
class CompletionDecision:
    accepted: bool
    reasons: tuple[str, ...]
    evidence_ids: tuple[str, ...] = ()


class CompletionGate:
    """Prevent a worker's own 'done' assertion from completing user-visible work."""

    TRUSTED_KINDS = frozenset({EvidenceKind.MACHINE_CHECK, EvidenceKind.VERIFIER_RESULT})

    def __init__(self, store: ProjectStore, evidence_store: EvidenceStore) -> None:
        self.store = store
        self.evidence_store = evidence_store

    async def try_complete(self, owner_id: str, project_id: str) -> CompletionDecision:
        project = await self.store.get_project(owner_id, project_id)
        if project is None:
            raise KeyError(project_id)

        tasks = await self.store.list_tasks(owner_id, project_id)
        evidence = await self.evidence_store.list_for_project(owner_id, project_id)
        reasons: list[str] = []

        unfinished = [task.task_id for task in tasks if task.state is not TaskState.COMPLETE]
        if unfinished:
            reasons.append("unfinished task: " + ", ".join(sorted(unfinished)))

        failed = [artifact.evidence_id for artifact in evidence if not artifact.passed]
        if failed:
            reasons.append("failed evidence: " + ", ".join(sorted(failed)))

        required_criteria: list[tuple[str, str | None]] = [
            (criterion, None) for criterion in project.acceptance_criteria
        ]
        for task in tasks:
            required_criteria.extend((criterion, task.task_id) for criterion in task.acceptance_criteria)

        accepted_evidence: list[EvidenceArtifact] = []
        for criterion, task_id in required_criteria:
            matches = [
                artifact
                for artifact in evidence
                if artifact.criterion == criterion
                and artifact.passed
                and artifact.kind in self.TRUSTED_KINDS
                and (task_id is None or artifact.task_id == task_id)
            ]
            if not matches:
                reasons.append(
                    f"criterion '{criterion}' requires machine-verifiable or verifier evidence"
                )
            else:
                accepted_evidence.extend(matches)

        if reasons:
            if project.state is not ProjectState.VERIFYING:
                verifying = project.transition(ProjectState.VERIFYING)
                await self.store.save_project(verifying, event="project.verification_rejected")
            return CompletionDecision(False, tuple(reasons))

        completed = project.transition(ProjectState.COMPLETE)
        await self.store.save_project(completed, event="project.complete.verified")
        ids = tuple(sorted({artifact.evidence_id for artifact in accepted_evidence}))
        return CompletionDecision(True, (), ids)
