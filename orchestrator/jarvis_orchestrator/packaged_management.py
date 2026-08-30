"""Minimal production composition for the APK-facing management plane."""

from __future__ import annotations

from .goal_planner import GoalRequest, PlannedTask
from .management import Task
from .management_service import ManagementService
from .runtime import AgentZeroRuntime, AgentZeroTaskWorker
from .worker_registry import WorkerNode


class DefaultPlanningHook:
    """Conservative provider-neutral fallback planner for the packaged service.

    Rich decomposition remains replaceable through the PlanningHook boundary.
    This fallback deliberately creates one executable graph node rather than
    inventing multi-step work when no planner model has been configured.
    """

    async def plan(self, request: GoalRequest) -> list[PlannedTask]:
        return [
            PlannedTask(
                key="execute",
                goal=request.goal,
                constraints=request.constraints,
                acceptance_criteria=request.acceptance_criteria,
                deadline=request.deadline,
                required_capabilities=("general",),
            )
        ]


class RuntimeTaskWorker:
    """Provider-neutral bridge for runtimes implementing JARVIS's execute seam."""

    def __init__(self, worker_id: str, runtime) -> None:
        self.worker_id = worker_id
        self.runtime = runtime

    @staticmethod
    def _prompt(task: Task) -> str:
        lines = [f"Goal: {task.goal}"]
        if task.constraints:
            lines.append("Constraints:")
            lines.extend(f"- {item}" for item in task.constraints)
        if task.acceptance_criteria:
            lines.append("Acceptance criteria:")
            lines.extend(f"- {item}" for item in task.acceptance_criteria)
        return "\n".join(lines)

    async def execute_task(self, task: Task, owner_id: str) -> str:
        session_id = f"{owner_id}:{task.project_id}:{self.worker_id}"
        return await self.runtime.execute(self._prompt(task), session_id)


async def build_packaged_management_service(*, store, registry, runtime) -> ManagementService:
    worker_id = "general-worker"
    await registry.register(WorkerNode(worker_id=worker_id, capabilities=("general",)))

    if isinstance(runtime, AgentZeroRuntime):
        worker = AgentZeroTaskWorker(worker_id, runtime)
    else:
        worker = RuntimeTaskWorker(worker_id, runtime)

    return ManagementService(
        store=store,
        registry=registry,
        planning_hook=DefaultPlanningHook(),
        workers={worker_id: worker},
    )
