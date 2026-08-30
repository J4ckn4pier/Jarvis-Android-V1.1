from __future__ import annotations

import asyncio
from datetime import datetime, timedelta, timezone

import pytest

from jarvis_orchestrator.goal_planner import GoalRequest, PlannedTask
from jarvis_orchestrator.management import ProjectState, TaskState
from jarvis_orchestrator.management_service import ManagementService
from jarvis_orchestrator.project_store import ValkeyProjectStore
from jarvis_orchestrator.verification import EvidenceKind
from jarvis_orchestrator.worker_registry import ValkeyWorkerRegistry, WorkerNode, WorkerStatus


class FakeValkey:
    def __init__(self):
        self.values = {}
        self.sets = {}
        self.streams = {}

    async def set(self, key, value):
        self.values[key] = value
        return True

    async def get(self, key):
        return self.values.get(key)

    async def sadd(self, key, *values):
        bucket = self.sets.setdefault(key, set())
        before = len(bucket)
        bucket.update(values)
        return len(bucket) - before

    async def smembers(self, key):
        return set(self.sets.get(key, set()))

    async def xadd(self, key, fields):
        rows = self.streams.setdefault(key, [])
        row_id = f"{len(rows) + 1}-0"
        rows.append((row_id, dict(fields)))
        return row_id

    async def xrange(self, key, min="-", max="+", count=None):
        rows = list(self.streams.get(key, []))
        return rows if count is None else rows[:count]


class PrototypePlanner:
    async def plan(self, request: GoalRequest):
        return [
            PlannedTask("research", "Research restaurants", required_capabilities=("research",)),
            PlannedTask("budget", "Check price fit", required_capabilities=("budget",)),
            PlannedTask(
                "synthesize",
                "Synthesize recommendation",
                dependencies=("research", "budget"),
                required_capabilities=("synthesis",),
            ),
            PlannedTask(
                "verify",
                "Independently verify recommendation",
                dependencies=("synthesize",),
                required_capabilities=("verification",),
                acceptance_criteria=("recommendation independently verified",),
            ),
        ]


class RecordingWorker:
    def __init__(self, worker_id: str, output: str, gate: asyncio.Event | None = None):
        self.worker_id = worker_id
        self.output = output
        self.gate = gate
        self.started = asyncio.Event()
        self.calls = 0

    async def execute_task(self, task, owner_id):
        self.calls += 1
        self.started.set()
        if self.gate is not None:
            await self.gate.wait()
        return self.output


@pytest.mark.asyncio
async def test_complex_goal_survives_stall_verification_restart_and_cursor_reconnect():
    now = datetime(2026, 8, 30, 20, 0, tzinfo=timezone.utc)
    valkey = FakeValkey()
    project_store = ValkeyProjectStore(valkey)
    registry = ValkeyWorkerRegistry(valkey)
    for node in (
        WorkerNode("research-a", capabilities=("research",)),
        WorkerNode("research-b", capabilities=("research",)),
        WorkerNode("budget", capabilities=("budget",)),
        WorkerNode("synth", capabilities=("synthesis",)),
        WorkerNode("verify", capabilities=("verification",)),
    ):
        await registry.register(node)

    release = asyncio.Event()
    research_a = RecordingWorker("research-a", "three candidate restaurants", release)
    research_b = RecordingWorker("research-b", "recovered research")
    budget = RecordingWorker("budget", "all candidates under $50", release)
    synth = RecordingWorker("synth", "Choose Thai Garden")
    verify = RecordingWorker("verify", "verified independently")

    ids = iter(("project-1", "task-r", "task-b", "task-s", "task-v"))
    service = ManagementService(
        store=project_store,
        registry=registry,
        planning_hook=PrototypePlanner(),
        workers={
            worker.worker_id: worker
            for worker in (research_a, research_b, budget, synth, verify)
        },
        id_factory=lambda: next(ids),
        stall_after=timedelta(minutes=5),
        clock=lambda: now,
    )

    submitted = await service.submit(
        "owner-a",
        "primary",
        GoalRequest(
            owner_id="owner-a",
            session_id="primary",
            goal="Find a good restaurant for dinner",
            constraints=("under $50",),
            acceptance_criteria=("recommendation independently verified",),
        ),
    )
    assert submitted["project_id"] == "project-1"

    # Two independent root tasks must genuinely start in parallel.
    first_wave = asyncio.create_task(service.run_ready("owner-a", "project-1"))
    await asyncio.wait_for(asyncio.gather(research_a.started.wait(), budget.started.wait()), timeout=1)
    assert not first_wave.done()

    # Simulate research-a becoming stale before it returns. Budget may finish.
    release.set()
    await first_wave
    tasks = {task.task_id: task for task in await project_store.list_tasks("owner-a", "project-1")}
    stale = tasks["task-r"]
    stale = stale.transition(TaskState.RUNNING)
    stale = stale.__class__(
        task_id=stale.task_id,
        project_id=stale.project_id,
        goal=stale.goal,
        constraints=stale.constraints,
        acceptance_criteria=stale.acceptance_criteria,
        dependencies=stale.dependencies,
        required_capabilities=stale.required_capabilities,
        assigned_workers=("research-a",),
        deadline=stale.deadline,
        state=TaskState.RUNNING,
        created_at=now - timedelta(minutes=10),
        updated_at=now - timedelta(minutes=10),
        last_progress_at=now - timedelta(minutes=10),
    )
    await project_store.save_task("owner-a", stale, event="test.induced_stall")
    await registry.set_status("research-a", WorkerStatus.BUSY)

    recovered = await service.recover_stalled("owner-a", "project-1")
    assert recovered["reassigned"] == ["task-r"]
    assert research_b.calls == 1
    assert research_a.calls == 1

    # Dependent synthesis then verification proceed only after prerequisites.
    await service.run_until_blocked("owner-a", "project-1")
    await service.record_evidence(
        "owner-a",
        "project-1",
        "task-v",
        "recommendation independently verified",
        EvidenceKind.VERIFIER_RESULT,
        True,
        "independent verifier agreed",
        "verify",
    )
    decision = await service.verify_and_complete("owner-a", "project-1")
    assert decision["state"] == ProjectState.COMPLETE.value
    assert decision["accepted"] is True

    before_restart = await service.result("owner-a", "project-1")
    assert before_restart["state"] == "complete"
    assert "Choose Thai Garden" in before_restart["result"]

    page = await service.events("owner-a", "project-1", None, 3)
    cursor = page["next_event_id"]
    assert cursor

    # New service objects over the same Valkey emulate orchestrator process restart.
    restarted = ManagementService(
        store=ValkeyProjectStore(valkey),
        registry=ValkeyWorkerRegistry(valkey),
        planning_hook=PrototypePlanner(),
        workers={},
        stall_after=timedelta(minutes=5),
        clock=lambda: now,
    )
    after_restart = await restarted.status("owner-a", "project-1")
    final = await restarted.result("owner-a", "project-1")
    resumed = await restarted.events("owner-a", "project-1", cursor, 100)

    assert after_restart["state"] == "complete"
    assert final == before_restart
    assert resumed["project_id"] == "project-1"
    assert all(event["event_id"] > cursor for event in resumed["events"])
    assert final["provider_details_exposed"] is False
