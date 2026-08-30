import pytest

from jarvis_orchestrator.management import Project, ProjectState, Task, TaskState
from jarvis_orchestrator.project_store import InMemoryProjectStore, ValkeyProjectStore


class FakeValkey:
    """Small redis-py compatible fake for deterministic project-store tests."""

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


class BrokenValkey(FakeValkey):
    async def get(self, key):
        raise ConnectionError("valkey unavailable")


async def _exercise_store(store):
    project = Project(
        project_id="project-1",
        owner_id="owner-a",
        session_id="primary",
        goal="Build durable management state",
        constraints=("open mandatory core",),
        acceptance_criteria=("survives restart",),
    )
    task = Task(
        task_id="task-1",
        project_id=project.project_id,
        goal="Persist task graph",
        dependencies=("task-0",),
    ).assign("worker-a").transition(TaskState.RUNNING)
    project = project.transition(ProjectState.ACTIVE)

    await store.save_project(project, event="project.active")
    await store.save_task(project.owner_id, task, event="task.running")
    return project, task


@pytest.mark.asyncio
async def test_in_memory_project_store_keeps_exact_state_and_events():
    store = InMemoryProjectStore()
    project, task = await _exercise_store(store)

    assert await store.get_project("owner-a", "project-1") == project
    assert await store.list_tasks("owner-a", "project-1") == [task]
    events = await store.events("owner-a", "project-1")
    assert [event.kind for event in events] == ["project.active", "task.running"]


@pytest.mark.asyncio
async def test_valkey_project_store_restores_exact_state_after_store_restart():
    valkey = FakeValkey()
    first = ValkeyProjectStore(valkey)
    project, task = await _exercise_store(first)

    restarted = ValkeyProjectStore(valkey)
    assert await restarted.get_project("owner-a", "project-1") == project
    assert await restarted.list_tasks("owner-a", "project-1") == [task]
    assert [event.kind for event in await restarted.events("owner-a", "project-1")] == [
        "project.active",
        "task.running",
    ]


@pytest.mark.asyncio
async def test_project_store_isolates_same_project_id_between_owners():
    store = InMemoryProjectStore()
    first = Project("same", "owner-a", "primary", "Owner A goal")
    second = Project("same", "owner-b", "primary", "Owner B goal")
    await store.save_project(first)
    await store.save_project(second)

    assert (await store.get_project("owner-a", "same")).goal == "Owner A goal"
    assert (await store.get_project("owner-b", "same")).goal == "Owner B goal"


@pytest.mark.asyncio
async def test_configured_valkey_failure_never_falls_back_to_process_memory():
    store = ValkeyProjectStore(BrokenValkey())

    with pytest.raises(ConnectionError, match="valkey unavailable"):
        await store.get_project("owner-a", "project-1")
