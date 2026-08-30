"""Provider-neutral task dispatch across the JARVIS worker graph."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Mapping, Protocol, Sequence

from .management import Task
from .worker_registry import WorkerRegistry, WorkerStatus


class TaskWorker(Protocol):
    """Minimal replaceable worker adapter used by the management plane."""

    worker_id: str

    async def execute_task(self, task: Task, owner_id: str) -> str: ...


@dataclass(frozen=True, slots=True)
class DispatchResult:
    task_id: str
    outputs: dict[str, str]


class MultiWorkerDispatcher:
    """Select workers by graph capability and execute task assignments concurrently."""

    def __init__(self, registry: WorkerRegistry, workers: Mapping[str, TaskWorker]) -> None:
        self.registry = registry
        self.workers = dict(workers)

    async def _worker_ids_for(self, task: Task) -> tuple[str, ...]:
        if task.assigned_workers:
            # Task already normalizes/deduplicates assignment edges.
            return task.assigned_workers

        if not task.required_capabilities:
            raise LookupError(f"task {task.task_id!r} has no worker assignment or required capability")

        candidates = None
        for capability in task.required_capabilities:
            matching = await self.registry.find_by_capability(capability)
            available_ids = {
                node.worker_id for node in matching if node.status is WorkerStatus.AVAILABLE
            }
            candidates = available_ids if candidates is None else candidates & available_ids

        worker_ids = sorted(candidates or ())
        if not worker_ids:
            required = ", ".join(task.required_capabilities)
            raise LookupError(f"no available worker satisfies task capabilities: {required}")
        # An unassigned task gets one deterministic worker. Multiple workers are an
        # explicit graph/planner decision represented by assigned_workers.
        return (worker_ids[0],)

    async def _execute(self, worker_id: str, task: Task, owner_id: str) -> tuple[str, str]:
        worker = self.workers.get(worker_id)
        if worker is None:
            raise LookupError(f"worker runtime is not connected: {worker_id}")
        return worker_id, await worker.execute_task(task, owner_id)

    async def dispatch(self, owner_id: str, task: Task) -> DispatchResult:
        worker_ids = await self._worker_ids_for(task)
        pairs = await asyncio.gather(
            *(self._execute(worker_id, task, owner_id) for worker_id in worker_ids)
        )
        return DispatchResult(task.task_id, dict(pairs))

    async def dispatch_many(
        self, owner_id: str, tasks: Sequence[Task]
    ) -> dict[str, DispatchResult]:
        results = await asyncio.gather(*(self.dispatch(owner_id, task) for task in tasks))
        return {result.task_id: result for result in results}
