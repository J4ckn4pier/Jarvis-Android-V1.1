from datetime import datetime, timezone

import pytest

from jarvis_orchestrator.goal_planner import GoalCompiler, GoalRequest, PlannedTask
from jarvis_orchestrator.project_store import InMemoryProjectStore
from jarvis_orchestrator.worker_registry import InMemoryWorkerRegistry, WorkerNode


class DeterministicPlanningHook:
    async def plan(self, request: GoalRequest) -> list[PlannedTask]:
        assert request.goal == "Find a good restaurant for dinner and verify the recommendation"
        return [
            PlannedTask(
                key="research",
                goal="Research suitable restaurants",
                required_capabilities=("research",),
            ),
            PlannedTask(
                key="compare",
                goal="Compare the researched options",
                dependencies=("research",),
                required_capabilities=("analysis",),
            ),
            PlannedTask(
                key="verify",
                goal="Independently verify the recommendation",
                dependencies=("compare",),
                required_capabilities=("verification",),
            ),
        ]


@pytest.mark.asyncio
async def test_complex_goal_compiles_to_persistent_graph_and_selects_workers_by_capability():
    store = InMemoryProjectStore()
    registry = InMemoryWorkerRegistry()
    await registry.register(WorkerNode("node-r", capabilities=("research",)))
    await registry.register(WorkerNode("node-a", capabilities=("analysis",)))
    await registry.register(WorkerNode("node-v", capabilities=("verification",)))

    ids = iter(("project-123", "task-r", "task-a", "task-v"))
    compiler = GoalCompiler(
        store=store,
        registry=registry,
        planning_hook=DeterministicPlanningHook(),
        id_factory=lambda: next(ids),
    )
    deadline = datetime(2026, 9, 1, 1, 0, tzinfo=timezone.utc)
    request = GoalRequest(
        owner_id="owner-a",
        session_id="primary",
        goal="Find a good restaurant for dinner and verify the recommendation",
        constraints=("under $50", "open mandatory core"),
        acceptance_criteria=("recommendation is independently checked",),
        deadline=deadline,
    )

    project = await compiler.compile(request)

    assert project.project_id == "project-123"
    assert project.owner_id == "owner-a"
    assert project.session_id == "primary"
    assert project.goal == request.goal
    assert project.constraints == request.constraints
    assert project.acceptance_criteria == request.acceptance_criteria
    assert project.deadline == deadline

    restored = await store.get_project("owner-a", "project-123")
    assert restored == project
    tasks = await store.list_tasks("owner-a", "project-123")
    assert [task.task_id for task in tasks] == ["task-a", "task-r", "task-v"]
    by_goal = {task.goal: task for task in tasks}

    research = by_goal["Research suitable restaurants"]
    compare = by_goal["Compare the researched options"]
    verify = by_goal["Independently verify the recommendation"]
    assert research.required_capabilities == ("research",)
    assert research.assigned_workers == ("node-r",)
    assert compare.dependencies == (research.task_id,)
    assert compare.required_capabilities == ("analysis",)
    assert compare.assigned_workers == ("node-a",)
    assert verify.dependencies == (compare.task_id,)
    assert verify.required_capabilities == ("verification",)
    assert verify.assigned_workers == ("node-v",)


@pytest.mark.asyncio
async def test_compiler_does_not_encode_worker_provider_names():
    store = InMemoryProjectStore()
    registry = InMemoryWorkerRegistry()
    await registry.register(WorkerNode("anything-compatible", capabilities=("research",)))

    class OneTaskHook:
        async def plan(self, request):
            return [PlannedTask("research", "Research", required_capabilities=("research",))]

    compiler = GoalCompiler(
        store=store,
        registry=registry,
        planning_hook=OneTaskHook(),
        id_factory=iter(("project-x", "task-x")).__next__,
    )
    project = await compiler.compile(GoalRequest("owner-a", "primary", "Research this"))
    tasks = await store.list_tasks("owner-a", project.project_id)

    assert tasks[0].assigned_workers == ("anything-compatible",)


@pytest.mark.asyncio
async def test_compiler_rejects_invalid_plans_before_persisting_partial_graph():
    store = InMemoryProjectStore()
    registry = InMemoryWorkerRegistry()
    await registry.register(WorkerNode("node-r", capabilities=("research",)))

    class BrokenHook:
        async def plan(self, request):
            return [
                PlannedTask(
                    key="research",
                    goal="Research",
                    dependencies=("missing-task",),
                    required_capabilities=("research",),
                )
            ]

    compiler = GoalCompiler(store, registry, BrokenHook(), id_factory=lambda: "unused")

    with pytest.raises(ValueError, match="unknown dependency"):
        await compiler.compile(GoalRequest("owner-a", "primary", "Research this"))

    assert await store.get_project("owner-a", "unused") is None
