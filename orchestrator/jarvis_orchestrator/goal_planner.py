"""Provider-neutral compilation of complex goals into persistent project graphs."""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import datetime
from typing import Callable, Protocol

from .management import Project, Task
from .project_store import ProjectStore
from .worker_registry import WorkerRegistry, WorkerStatus


@dataclass(frozen=True, slots=True)
class GoalRequest:
    owner_id: str
    session_id: str
    goal: str
    constraints: tuple[str, ...] = ()
    acceptance_criteria: tuple[str, ...] = ()
    deadline: datetime | None = None


@dataclass(frozen=True, slots=True)
class PlannedTask:
    """Storage/provider-neutral task description emitted by a planning hook."""

    key: str
    goal: str
    dependencies: tuple[str, ...] = ()
    required_capabilities: tuple[str, ...] = ()
    constraints: tuple[str, ...] = ()
    acceptance_criteria: tuple[str, ...] = ()
    deadline: datetime | None = None
    worker_count: int = 1


class PlanningHook(Protocol):
    """Replaceable planner seam; deterministic today, local/remote LLM later."""

    async def plan(self, request: GoalRequest) -> list[PlannedTask]: ...


class GoalCompiler:
    def __init__(
        self,
        store: ProjectStore,
        registry: WorkerRegistry,
        planning_hook: PlanningHook,
        id_factory: Callable[[], str] | None = None,
    ) -> None:
        self.store = store
        self.registry = registry
        self.planning_hook = planning_hook
        self.id_factory = id_factory or (lambda: str(uuid.uuid4()))

    @staticmethod
    def _validate_plan(specs: list[PlannedTask]) -> None:
        if not specs:
            raise ValueError("planner returned no tasks")
        keys = [spec.key for spec in specs]
        if len(keys) != len(set(keys)):
            raise ValueError("planner returned duplicate task key")
        known = set(keys)
        for spec in specs:
            if not spec.key or not spec.goal:
                raise ValueError("planned tasks require key and goal")
            if spec.worker_count < 1:
                raise ValueError("worker_count must be at least one")
            for dependency in spec.dependencies:
                if dependency not in known:
                    raise ValueError(f"unknown dependency: {dependency}")
                if dependency == spec.key:
                    raise ValueError(f"task cannot depend on itself: {spec.key}")

        # Reject cycles before any durable write.
        dependencies = {spec.key: set(spec.dependencies) for spec in specs}
        visiting: set[str] = set()
        visited: set[str] = set()

        def visit(key: str) -> None:
            if key in visited:
                return
            if key in visiting:
                raise ValueError("task dependency cycle")
            visiting.add(key)
            for dependency in dependencies[key]:
                visit(dependency)
            visiting.remove(key)
            visited.add(key)

        for key in keys:
            visit(key)

    async def _select_workers(self, spec: PlannedTask) -> tuple[str, ...]:
        if not spec.required_capabilities:
            return ()
        candidates: set[str] | None = None
        for capability in spec.required_capabilities:
            matching = await self.registry.find_by_capability(capability)
            available = {
                node.worker_id for node in matching if node.status is WorkerStatus.AVAILABLE
            }
            candidates = available if candidates is None else candidates & available
        selected = tuple(sorted(candidates or ())[: spec.worker_count])
        if len(selected) < spec.worker_count:
            required = ", ".join(spec.required_capabilities)
            raise LookupError(f"not enough available workers for capabilities: {required}")
        return selected

    async def compile(self, request: GoalRequest) -> Project:
        specs = list(await self.planning_hook.plan(request))
        self._validate_plan(specs)

        # Resolve every worker before creating IDs or writing partial durable state.
        selected_workers: dict[str, tuple[str, ...]] = {}
        for spec in specs:
            selected_workers[spec.key] = await self._select_workers(spec)

        project_id = self.id_factory()
        task_ids = {spec.key: self.id_factory() for spec in specs}
        project = Project(
            project_id=project_id,
            owner_id=request.owner_id,
            session_id=request.session_id,
            goal=request.goal,
            constraints=tuple(request.constraints),
            acceptance_criteria=tuple(request.acceptance_criteria),
            deadline=request.deadline,
        )
        tasks = [
            Task(
                task_id=task_ids[spec.key],
                project_id=project_id,
                goal=spec.goal,
                constraints=tuple(spec.constraints),
                acceptance_criteria=tuple(spec.acceptance_criteria),
                dependencies=tuple(task_ids[key] for key in spec.dependencies),
                required_capabilities=tuple(spec.required_capabilities),
                assigned_workers=selected_workers[spec.key],
                deadline=spec.deadline or request.deadline,
            )
            for spec in specs
        ]

        await self.store.save_project(project, event="project.compiled")
        for task in tasks:
            await self.store.save_task(request.owner_id, task, event="task.compiled")
        return project
