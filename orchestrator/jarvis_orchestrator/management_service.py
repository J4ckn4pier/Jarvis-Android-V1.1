"""Thin composition layer for the JARVIS management-plane prototype."""

from __future__ import annotations

import asyncio
from dataclasses import asdict
from datetime import datetime, timedelta, timezone
from typing import Callable, Mapping

from .goal_planner import GoalCompiler, GoalRequest, PlanningHook
from .management import ProjectState, Task, TaskState
from .multi_worker import MultiWorkerDispatcher, TaskWorker
from .project_store import ProjectStore
from .supervisor import WatchdogSupervisor
from .verification import (
    CompletionGate,
    EvidenceArtifact,
    EvidenceKind,
    InMemoryEvidenceStore,
)
from .worker_registry import WorkerRegistry


def _now() -> datetime:
    return datetime.now(timezone.utc)


class ManagementService:
    """Coordinate planning, execution, recovery, verification, and public status.

    The service deliberately composes the milestone primitives instead of hiding a
    second orchestration framework inside JARVIS. Worker/provider identities stay
    behind this boundary; phone-facing methods return only JARVIS project state.
    """

    def __init__(
        self,
        *,
        store: ProjectStore,
        registry: WorkerRegistry,
        planning_hook: PlanningHook,
        workers: Mapping[str, TaskWorker],
        id_factory: Callable[[], str] | None = None,
        stall_after: timedelta = timedelta(minutes=5),
        clock: Callable[[], datetime] = _now,
    ) -> None:
        self.store = store
        self.registry = registry
        self.clock = clock
        self.dispatcher = MultiWorkerDispatcher(registry, workers)
        self.compiler = GoalCompiler(store, registry, planning_hook, id_factory=id_factory)
        self.supervisor = WatchdogSupervisor(
            store,
            registry,
            self.dispatcher,
            stall_after,
            clock=clock,
        )
        self.evidence_store = InMemoryEvidenceStore()
        self.completion_gate = CompletionGate(store, self.evidence_store)
        self._evidence_sequence = 0

    async def _project(self, owner_id: str, project_id: str):
        project = await self.store.get_project(owner_id, project_id)
        if project is None:
            raise KeyError(project_id)
        return project

    @staticmethod
    def _goal_request(owner_id: str, session_id: str, request) -> GoalRequest:
        if isinstance(request, GoalRequest):
            return request
        return GoalRequest(
            owner_id=owner_id,
            session_id=session_id,
            goal=request.goal,
            constraints=tuple(request.constraints),
            acceptance_criteria=tuple(request.acceptance_criteria),
            deadline=request.deadline,
        )

    async def submit(self, owner_id: str, session_id: str, request) -> dict:
        compiled = await self.compiler.compile(self._goal_request(owner_id, session_id, request))
        active = compiled.transition(ProjectState.ACTIVE)
        await self.store.save_project(active, event="project.active")
        return {
            "project_id": active.project_id,
            "session_id": active.session_id,
            "state": active.state.value,
            "goal": active.goal,
            "provider_details_exposed": False,
        }

    async def _ready_tasks(self, owner_id: str, project_id: str) -> list[Task]:
        tasks = await self.store.list_tasks(owner_id, project_id)
        completed = {task.task_id for task in tasks if task.state is TaskState.COMPLETE}
        runnable_states = {TaskState.PENDING, TaskState.ASSIGNED}
        return [
            task
            for task in tasks
            if task.state in runnable_states and all(dep in completed for dep in task.dependencies)
        ]

    async def _run_one(self, owner_id: str, task: Task):
        outcome = await self.supervisor.run_task(owner_id, task)
        if outcome.outputs:
            # A task may be explicitly cross-checked by several peers. Preserve all
            # outputs deterministically without exposing worker names to clients.
            output = "\n".join(outcome.outputs[key] for key in sorted(outcome.outputs))
            await self.store.save_task_output(owner_id, task.project_id, task.task_id, output)
        return outcome

    async def run_ready(self, owner_id: str, project_id: str) -> dict:
        await self._project(owner_id, project_id)
        ready = await self._ready_tasks(owner_id, project_id)
        if not ready:
            return {"ran": [], "blocked": True}
        outcomes = await asyncio.gather(*(self._run_one(owner_id, task) for task in ready))
        return {
            "ran": [outcome.task.task_id for outcome in outcomes],
            "blocked": False,
        }

    async def run_until_blocked(self, owner_id: str, project_id: str) -> dict:
        ran: list[str] = []
        while True:
            ready = await self._ready_tasks(owner_id, project_id)
            if not ready:
                break
            outcomes = await asyncio.gather(*(self._run_one(owner_id, task) for task in ready))
            ran.extend(outcome.task.task_id for outcome in outcomes)
        status = await self.status(owner_id, project_id)
        status["ran"] = ran
        return status

    async def recover_stalled(self, owner_id: str, project_id: str) -> dict:
        await self._project(owner_id, project_id)
        reassigned: list[str] = []
        escalated: list[str] = []
        for task in await self.store.list_tasks(owner_id, project_id):
            if task.state is not TaskState.RUNNING:
                continue
            last_progress = task.last_progress_at or task.updated_at or task.created_at
            if self.clock() - last_progress < self.supervisor.stall_after:
                continue
            previous_workers = task.assigned_workers
            outcome = await self.supervisor.recover_stalled(owner_id, task)
            if outcome.outputs:
                output = "\n".join(outcome.outputs[key] for key in sorted(outcome.outputs))
                await self.store.save_task_output(owner_id, project_id, task.task_id, output)
            if outcome.task.assigned_workers != previous_workers and outcome.task.state is TaskState.COMPLETE:
                reassigned.append(task.task_id)
            if outcome.needs_user_escalation:
                escalated.append(task.task_id)
        return {"reassigned": reassigned, "escalated": escalated}

    async def record_evidence(
        self,
        owner_id: str,
        project_id: str,
        task_id: str | None,
        criterion: str,
        kind: EvidenceKind,
        passed: bool,
        detail: str,
        source_worker_id: str | None = None,
    ) -> dict:
        await self._project(owner_id, project_id)
        self._evidence_sequence += 1
        evidence_id = f"evidence-{self._evidence_sequence:08d}"
        artifact = EvidenceArtifact(
            evidence_id=evidence_id,
            project_id=project_id,
            task_id=task_id,
            criterion=criterion,
            kind=kind,
            passed=passed,
            detail=detail,
            source_worker_id=source_worker_id,
            created_at=self.clock(),
        )
        await self.evidence_store.record(owner_id, artifact)
        return {"evidence_id": evidence_id, "recorded": True}

    async def verify_and_complete(self, owner_id: str, project_id: str) -> dict:
        decision = await self.completion_gate.try_complete(owner_id, project_id)
        project = await self._project(owner_id, project_id)
        return {
            "project_id": project_id,
            "state": project.state.value,
            "accepted": decision.accepted,
            "reasons": list(decision.reasons),
            "evidence_ids": list(decision.evidence_ids),
        }

    async def status(self, owner_id: str, project_id: str) -> dict:
        project = await self._project(owner_id, project_id)
        tasks = await self.store.list_tasks(owner_id, project_id)
        counts: dict[str, int] = {}
        for task in tasks:
            counts[task.state.value] = counts.get(task.state.value, 0) + 1
        return {
            "project_id": project.project_id,
            "session_id": project.session_id,
            "goal": project.goal,
            "state": project.state.value,
            "task_count": len(tasks),
            "task_states": counts,
            "last_progress_at": project.last_progress_at.isoformat(),
            "provider_details_exposed": False,
        }

    async def result(self, owner_id: str, project_id: str) -> dict:
        project = await self._project(owner_id, project_id)
        outputs = await self.store.task_outputs(owner_id, project_id)
        synthesized = "\n".join(outputs[task_id] for task_id in sorted(outputs))
        return {
            "project_id": project.project_id,
            "state": project.state.value,
            "result": synthesized,
            "provider_details_exposed": False,
        }

    async def events(
        self,
        owner_id: str,
        project_id: str,
        after_event_id: str | None,
        limit: int,
    ) -> dict:
        await self._project(owner_id, project_id)
        stored = await self.store.events(owner_id, project_id)
        public = [
            {
                "event_id": f"{index:012d}",
                "project_id": event.project_id,
                "kind": event.kind,
                "task_id": event.task_id,
                "timestamp": event.timestamp.isoformat(),
            }
            for index, event in enumerate(stored, start=1)
        ]
        start = 0
        if after_event_id is not None:
            ids = [event["event_id"] for event in public]
            if after_event_id not in ids:
                raise KeyError(after_event_id)
            start = ids.index(after_event_id) + 1
        page = public[start : start + limit]
        next_event_id = page[-1]["event_id"] if page else after_event_id
        return {
            "project_id": project_id,
            "events": page,
            "next_event_id": next_event_id,
            "has_more": start + len(page) < len(public),
        }

    async def approve(
        self,
        owner_id: str,
        project_id: str,
        approval_id: str,
        approved: bool,
        response: str | None,
    ) -> dict:
        project = await self._project(owner_id, project_id)
        await self.store.save_project(
            project,
            event=f"approval.{approval_id}.{'approved' if approved else 'rejected'}",
        )
        return {
            "project_id": project_id,
            "approval_id": approval_id,
            "approved": approved,
            "response": response,
        }

    async def cancel(self, owner_id: str, project_id: str) -> dict:
        project = await self.supervisor.cancel_project(owner_id, project_id)
        return {"project_id": project_id, "state": project.state.value}
