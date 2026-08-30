"""Deterministic watchdog/supervisor for long-running JARVIS project work."""

from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime, timedelta, timezone
from typing import Callable

from .management import ProjectState, Task, TaskState
from .multi_worker import MultiWorkerDispatcher
from .project_store import ProjectStore
from .worker_registry import WorkerRegistry, WorkerStatus


def _now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass(frozen=True, slots=True)
class SupervisorOutcome:
    task: Task
    outputs: dict[str, str]
    attempts: int
    blocker: str | None = None
    needs_user_escalation: bool = False


class WatchdogSupervisor:
    """Detect stalled work, recover autonomously, and stop cleanly on cancellation."""

    def __init__(
        self,
        store: ProjectStore,
        registry: WorkerRegistry,
        dispatcher: MultiWorkerDispatcher,
        stall_after: timedelta,
        *,
        clock: Callable[[], datetime] = _now,
    ) -> None:
        self.store = store
        self.registry = registry
        self.dispatcher = dispatcher
        self.stall_after = stall_after
        self.clock = clock
        self._attempts: dict[tuple[str, str, str], int] = {}

    def _key(self, owner_id: str, task: Task) -> tuple[str, str, str]:
        return owner_id, task.project_id, task.task_id

    def _attempt_count(self, owner_id: str, task: Task) -> int:
        key = self._key(owner_id, task)
        if key not in self._attempts:
            # A RUNNING task with an assignment represents one already-started attempt.
            self._attempts[key] = 1 if task.state is TaskState.RUNNING else 0
        return self._attempts[key]

    def _increment_attempt(self, owner_id: str, task: Task) -> int:
        key = self._key(owner_id, task)
        current = self._attempt_count(owner_id, task)
        self._attempts[key] = current + 1
        return self._attempts[key]

    async def _is_project_cancelled(self, owner_id: str, task: Task) -> bool:
        project = await self.store.get_project(owner_id, task.project_id)
        return project is not None and project.state is ProjectState.CANCELLED

    async def run_task(self, owner_id: str, task: Task) -> SupervisorOutcome:
        attempts = self._attempt_count(owner_id, task)
        if task.state in (TaskState.COMPLETE, TaskState.CANCELLED):
            return SupervisorOutcome(task, {}, attempts)
        if await self._is_project_cancelled(owner_id, task):
            cancelled = task.transition(TaskState.CANCELLED)
            await self.store.save_task(owner_id, cancelled, event="task.cancelled")
            return SupervisorOutcome(cancelled, {}, attempts)

        attempts = self._increment_attempt(owner_id, task)
        running = task.transition(TaskState.RUNNING)
        await self.store.save_task(owner_id, running, event="task.running")
        result = await self.dispatcher.dispatch(owner_id, running)
        completed = running.transition(TaskState.COMPLETE)
        await self.store.save_task(owner_id, completed, event="task.complete")
        return SupervisorOutcome(completed, result.outputs, attempts)

    async def _available_replacement(self, task: Task) -> str | None:
        if not task.required_capabilities:
            return None
        candidates: set[str] | None = None
        for capability in task.required_capabilities:
            matching = await self.registry.find_by_capability(capability)
            available = {
                node.worker_id
                for node in matching
                if node.status is WorkerStatus.AVAILABLE
                and node.worker_id not in task.assigned_workers
            }
            candidates = available if candidates is None else candidates & available
        choices = sorted(candidates or ())
        return choices[0] if choices else None

    async def recover_stalled(self, owner_id: str, task: Task) -> SupervisorOutcome:
        attempts = self._attempt_count(owner_id, task)
        if task.state in (TaskState.COMPLETE, TaskState.CANCELLED):
            return SupervisorOutcome(task, {}, attempts)
        if await self._is_project_cancelled(owner_id, task):
            cancelled = task.transition(TaskState.CANCELLED)
            await self.store.save_task(owner_id, cancelled, event="task.cancelled")
            return SupervisorOutcome(cancelled, {}, attempts)

        last_progress = task.last_progress_at or task.updated_at or task.created_at
        if self.clock() - last_progress < self.stall_after:
            return SupervisorOutcome(task, {}, attempts)

        for worker_id in task.assigned_workers:
            node = await self.registry.get(worker_id)
            if node is not None and node.status is not WorkerStatus.OFFLINE:
                await self.registry.set_status(worker_id, WorkerStatus.DEGRADED)

        replacement = await self._available_replacement(task)
        if replacement is None:
            blocked = task.transition(TaskState.BLOCKED)
            await self.store.save_task(owner_id, blocked, event="task.blocked.no_replacement")
            return SupervisorOutcome(
                blocked,
                {},
                attempts,
                blocker="no compatible available worker",
                needs_user_escalation=True,
            )

        reassigned = replace(
            task,
            assigned_workers=(replacement,),
            state=TaskState.ASSIGNED,
            updated_at=self.clock(),
            last_progress_at=self.clock(),
        )
        await self.store.save_task(owner_id, reassigned, event="task.reassigned")
        return await self.run_task(owner_id, reassigned)

    async def cancel_project(self, owner_id: str, project_id: str):
        project = await self.store.get_project(owner_id, project_id)
        if project is None:
            raise KeyError(project_id)
        if project.state is not ProjectState.CANCELLED:
            project = project.transition(ProjectState.CANCELLED)
            await self.store.save_project(project, event="project.cancelled")

        terminal = {TaskState.COMPLETE, TaskState.FAILED, TaskState.CANCELLED}
        for task in await self.store.list_tasks(owner_id, project_id):
            if task.state in terminal:
                continue
            cancelled = task.transition(TaskState.CANCELLED)
            await self.store.save_task(owner_id, cancelled, event="task.cancelled")
        return project
