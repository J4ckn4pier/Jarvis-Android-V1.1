import pytest

from jarvis_orchestrator.worker_registry import (
    InMemoryWorkerRegistry,
    ValkeyWorkerRegistry,
    WorkerNode,
    WorkerRelation,
    WorkerStatus,
)


class FakeValkey:
    def __init__(self):
        self.values = {}
        self.sets = {}

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


async def _exercise_registry(registry):
    researcher = WorkerNode(
        worker_id="worker-research",
        capabilities=("research", "web"),
        endpoint="http://worker-research",
    )
    verifier = WorkerNode(
        worker_id="worker-verify",
        capabilities=("verification", "research"),
        endpoint="http://worker-verify",
    )
    supervisor = WorkerNode(
        worker_id="worker-supervisor",
        capabilities=("planning", "supervision"),
        endpoint="a2a://worker-supervisor",
    )

    await registry.register(researcher)
    await registry.register(verifier)
    await registry.register(supervisor)
    await registry.connect("worker-research", "worker-verify", WorkerRelation.PEER)
    await registry.connect("worker-supervisor", "worker-research", WorkerRelation.SUPERVISOR)
    await registry.connect("worker-verify", "worker-research", WorkerRelation.VERIFIER)
    await registry.connect("worker-research", "worker-supervisor", WorkerRelation.A2A)
    await registry.set_status("worker-research", WorkerStatus.BUSY)
    await registry.mark_progress("worker-research")
    return researcher, verifier, supervisor


@pytest.mark.asyncio
async def test_registry_queries_workers_by_capability_not_provider_name():
    registry = InMemoryWorkerRegistry()
    await _exercise_registry(registry)

    research = await registry.find_by_capability("research")
    assert [node.worker_id for node in research] == ["worker-research", "worker-verify"]
    assert all("agent-zero" not in node.worker_id for node in research)


@pytest.mark.asyncio
async def test_registry_supports_brain_graph_relations_and_runtime_status():
    registry = InMemoryWorkerRegistry()
    await _exercise_registry(registry)

    researcher = await registry.get("worker-research")
    assert researcher.status is WorkerStatus.BUSY
    assert researcher.last_progress_at >= researcher.registered_at

    relations = await registry.relations("worker-research")
    assert ("worker-verify", WorkerRelation.PEER) in relations
    assert ("worker-supervisor", WorkerRelation.A2A) in relations

    verifier_relations = await registry.relations("worker-verify")
    assert ("worker-research", WorkerRelation.VERIFIER) in verifier_relations


@pytest.mark.asyncio
async def test_valkey_registry_reloads_nodes_relations_status_and_progress():
    valkey = FakeValkey()
    first = ValkeyWorkerRegistry(valkey)
    await _exercise_registry(first)

    restarted = ValkeyWorkerRegistry(valkey)
    researcher = await restarted.get("worker-research")
    assert researcher.status is WorkerStatus.BUSY
    assert researcher.capabilities == ("research", "web")
    assert ("worker-verify", WorkerRelation.PEER) in await restarted.relations("worker-research")
    assert [node.worker_id for node in await restarted.find_by_capability("verification")] == [
        "worker-verify"
    ]


@pytest.mark.asyncio
async def test_registering_same_worker_updates_snapshot_without_duplicate_index_entries():
    registry = InMemoryWorkerRegistry()
    await registry.register(WorkerNode("worker-1", capabilities=("research",)))
    await registry.register(WorkerNode("worker-1", capabilities=("research", "verification")))

    assert [node.worker_id for node in await registry.find_by_capability("research")] == ["worker-1"]
    assert (await registry.get("worker-1")).capabilities == ("research", "verification")
