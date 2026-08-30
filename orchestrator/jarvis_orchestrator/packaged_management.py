"""Minimal production composition for the APK-facing management plane."""

from __future__ import annotations

import asyncio

from redis.exceptions import RedisError

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


class PackagedManagementService(ManagementService):
    """Management service that owns submitted work after the client disconnects."""

    def __init__(self, *args, worker_node: WorkerNode, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self.worker_node = worker_node
        self._background_tasks: set[asyncio.Task] = set()

    async def ensure_worker_registered(self) -> None:
        await self.registry.register(self.worker_node)

    def _track(self, coroutine) -> None:
        task = asyncio.create_task(coroutine)
        self._background_tasks.add(task)

        def finished(done: asyncio.Task) -> None:
            self._background_tasks.discard(done)
            if not done.cancelled():
                # Retrieve the exception so asyncio does not emit an unobserved
                # task warning. Durable project/task state remains authoritative.
                done.exception()

        task.add_done_callback(finished)

    async def _drive_project(self, owner_id: str, project_id: str) -> None:
        await self.run_until_blocked(owner_id, project_id)
        # CompletionGate is deliberately strict: projects with acceptance
        # criteria remain VERIFYING until trusted evidence exists. Simple goals
        # with no outstanding criteria can finish autonomously.
        await self.verify_and_complete(owner_id, project_id)

    async def submit(self, owner_id: str, session_id: str, request) -> dict:
        # Registration lives in durable Valkey in production. Reassert it at the
        # point of use so a process can start degraded while Valkey is offline,
        # then recover naturally once the dependency returns.
        await self.ensure_worker_registered()
        response = await super().submit(owner_id, session_id, request)
        self._track(self._drive_project(owner_id, response["project_id"]))
        return response

    async def aclose(self) -> None:
        # A process restart must never hang waiting for an external worker. Any
        # in-flight coroutine is cancelled; durable management state is retained
        # for restart/recovery instead of tying project lifetime to one process.
        pending = tuple(self._background_tasks)
        for task in pending:
            task.cancel()
        if pending:
            await asyncio.gather(*pending, return_exceptions=True)
        self._background_tasks.clear()


async def build_packaged_management_service(*, store, registry, runtime) -> ManagementService:
    worker_id = "general-worker"
    worker_node = WorkerNode(worker_id=worker_id, capabilities=("general",))

    if isinstance(runtime, AgentZeroRuntime):
        worker = AgentZeroTaskWorker(worker_id, runtime)
    else:
        worker = RuntimeTaskWorker(worker_id, runtime)

    service = PackagedManagementService(
        store=store,
        registry=registry,
        planning_hook=DefaultPlanningHook(),
        workers={worker_id: worker},
        worker_node=worker_node,
    )
    try:
        await service.ensure_worker_registered()
    except RedisError:
        # The existing health/readiness contract intentionally keeps the process
        # alive while Valkey is unavailable: /health remains useful, /ready is
        # 503, and normal service resumes when Valkey returns.
        pass
    return service
