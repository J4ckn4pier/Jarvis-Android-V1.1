"""Thin composition layer for the JARVIS management-plane prototype."""

from __future__ import annotations

import asyncio
from dataclasses import replace
from datetime import datetime, timedelta, timezone
from typing import Callable, Mapping
from uuid import uuid4

from .goal_planner import GoalCompiler, GoalRequest, PlanningHook
from .management import ProjectState, Task, TaskState
from .multi_worker import MultiWorkerDispatcher, TaskWorker
from .project_store import ProjectStore
from .supervisor import WatchdogSupervisor
from .verification import (
    CompletionGate,
    EvidenceArtifact,
    EvidenceKind,
    ProjectEvidenceStore,
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

    _APPROVAL_SEPARATOR = "|"

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
        self.evidence_store = ProjectEvidenceStore(store)
        self.completion_gate = CompletionGate(store, self.evidence_store)

    async def _project(self, owner_id: str, project_id: str):
        project = await self.store.get_project(owner_id, project_id)
        if project is None:
            raise KeyError(project_id)
        return project

    async def _mark_project_progress(self, owner_id: str, project_id: str) -> None:
        project = await self._project(owner_id, project_id)
        timestamp = self.clock()
        progressed = replace(project, updated_at=timestamp, last_progress_at=timestamp)
        await self.store.save_project(progressed, event="project.progress")

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

    @classmethod
    def _encode_approval_event(
        cls,
        kind: str,
        approval_id: str,
        task_id: str | None,
    ) -> str:
        return cls._APPROVAL_SEPARATOR.join((kind, approval_id, task_id or ""))

    @classmethod
    def _decode_approval_event(cls, kind: str) -> tuple[str, str | None, str | None]:
        parts = kind.split(cls._APPROVAL_SEPARATOR, 2)
        if len(parts) != 3 or parts[0] not in {
            "approval.requested",
            "approval.approved",
            "approval.rejected",
        }:
            return kind, None, None
        return parts[0], parts[1] or None, parts[2] or None

    @classmethod
    def _pending_approvals(cls, stored_events) -> list[dict[str, str | None]]:
        pending: dict[str, dict[str, str | None]] = {}
        for event in stored_events:
            kind, approval_id, task_id = cls._decode_approval_event(event.kind)
            if approval_id is None:
                continue
            if kind == "approval.requested":
                pending[approval_id] = {
                    "approval_id": approval_id,
                    "task_id": task_id,
                }
            elif kind in {"approval.approved", "approval.rejected"}:
                pending.pop(approval_id, None)
        return list(pending.values())

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
            output = "\n".join(outcome.outputs[key] for key in sorted(outcome.outputs))
            await self.store.save_task_output(owner_id, task.project_id, task.task_id, output)
        if outcome.task.state is not task.state or outcome.outputs or outcome.blocker:
            await self._mark_project_progress(owner_id, task.project_id)
        if outcome.needs_user_escalation:
            await self.request_approval(
                owner_id,
                task.project_id,
                f"approval-{uuid4().hex}",
                task_id=task.task_id,
            )
        return outcome

    async def run_ready(self, owner_id: str, project_id: str) -> dict:
        await self._project(owner_id, project_id)
        ready = await self._ready_tasks(owner_id, project_id)
        if not ready:
            return {"ran": [], "blocked": True}
        outcomes = await asyncio.gather(*(self._run_one(owner_id, task) for task in ready))
        return {"ran": [outcome.task.task_id for outcome in outcomes], "blocked": False}

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
            if outcome.task.state is not task.state or outcome.outputs or outcome.blocker:
                await self._mark_project_progress(owner_id, project_id)
            if outcome.task.assigned_workers != previous_workers and outcome.task.state is TaskState.COMPLETE:
                reassigned.append(task.task_id)
            if outcome.needs_user_escalation:
                await self.request_approval(
                    owner_id,
                    project_id,
                    f"approval-{uuid4().hex}",
                    task_id=task.task_id,
                )
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
        evidence_id = f"evidence-{uuid4().hex}"
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
        stored_events = await self.store.events(owner_id, project_id)
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
            "pending_approvals": self._pending_approvals(stored_events),
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

    async def morning_summary(self, owner_id: str, project_id: str) -> dict:
        """Rebuild a concise human-facing project briefing from durable state only."""

        project = await self._project(owner_id, project_id)
        tasks = await self.store.list_tasks(owner_id, project_id)
        stored_events = await self.store.events(owner_id, project_id)
        outputs = await self.store.task_outputs(owner_id, project_id)

        completed = sum(task.state is TaskState.COMPLETE for task in tasks)
        recoveries = sum(event.kind == "task.reassigned" for event in stored_events)
        pending_approvals = len(self._pending_approvals(stored_events))
        synthesized = "\n".join(outputs[task_id] for task_id in sorted(outputs))

        briefing_parts = [
            f"{project.goal}: {project.state.value}; {completed}/{len(tasks)} tasks complete."
        ]
        if recoveries:
            noun = "task" if recoveries == 1 else "tasks"
            briefing_parts.append(f"JARVIS recovered {recoveries} stalled {noun} automatically.")
        if pending_approvals:
            noun = "decision" if pending_approvals == 1 else "decisions"
            briefing_parts.append(f"{pending_approvals} {noun} need your approval.")
        if synthesized:
            briefing_parts.append(f"Result: {synthesized}")

        return {
            "project_id": project.project_id,
            "state": project.state.value,
            "completed_tasks": completed,
            "total_tasks": len(tasks),
            "recoveries": recoveries,
            "pending_approvals": pending_approvals,
            "briefing": " ".join(briefing_parts),
            "provider_details_exposed": False,
        }

    async def events(self, owner_id: str, project_id: str, after_event_id: str | None, limit: int) -> dict:
        await self._project(owner_id, project_id)
        stored = await self.store.events(owner_id, project_id)
        public = []
        for index, event in enumerate(stored, start=1):
            kind, approval_id, approval_task_id = self._decode_approval_event(event.kind)
            payload = {
                "event_id": f"{index:012d}",
                "project_id": event.project_id,
                "kind": kind,
                "task_id": approval_task_id if approval_id is not None else event.task_id,
                "timestamp": event.timestamp.isoformat(),
            }
            if approval_id is not None:
                payload["approval_id"] = approval_id
            public.append(payload)
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

    async def request_approval(
        self,
        owner_id: str,
        project_id: str,
        approval_id: str,
        *,
        task_id: str | None = None,
    ) -> dict:
        project = await self._project(owner_id, project_id)
        if not approval_id or approval_id.strip() != approval_id or self._APPROVAL_SEPARATOR in approval_id:
            raise ValueError("approval_id must be a non-empty exact identifier")
        if task_id is not None and (
            not task_id or task_id.strip() != task_id or self._APPROVAL_SEPARATOR in task_id
        ):
            raise ValueError("task_id must be an exact identifier")
        await self.store.save_project(
            project,
            event=self._encode_approval_event("approval.requested", approval_id, task_id),
        )
        return {
            "project_id": project_id,
            "approval_id": approval_id,
            "task_id": task_id,
            "state": "pending",
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
        pending = {
            item["approval_id"]: item
            for item in self._pending_approvals(await self.store.events(owner_id, project_id))
        }
        approval = pending.get(approval_id)
        if approval is None:
            raise KeyError(approval_id)
        task_id = approval["task_id"]
        await self.store.save_project(
            project,
            event=self._encode_approval_event(
                "approval.approved" if approved else "approval.rejected",
                approval_id,
                task_id,
            ),
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
