"""Provider-neutral worker graph and durable registry contracts."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field, replace
from datetime import datetime, timezone
from enum import Enum
from typing import Iterable, Protocol


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _dedupe(values: Iterable[str]) -> tuple[str, ...]:
    return tuple(dict.fromkeys(values))


def _decode(value):
    if isinstance(value, bytes):
        return value.decode()
    return value


def _hash(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


class WorkerStatus(str, Enum):
    AVAILABLE = "available"
    BUSY = "busy"
    DEGRADED = "degraded"
    OFFLINE = "offline"


class WorkerRelation(str, Enum):
    PEER = "peer"
    SUPERVISOR = "supervisor"
    VERIFIER = "verifier"
    A2A = "a2a"
    SPECIALIST = "specialist"


@dataclass(frozen=True, slots=True)
class WorkerNode:
    """One replaceable cognitive/worker node in the JARVIS backend graph."""

    worker_id: str
    capabilities: tuple[str, ...] = ()
    endpoint: str | None = None
    status: WorkerStatus = WorkerStatus.AVAILABLE
    registered_at: datetime = field(default_factory=_now)
    updated_at: datetime | None = None
    last_heartbeat_at: datetime | None = None
    last_progress_at: datetime | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "capabilities", _dedupe(self.capabilities))
        if self.updated_at is None:
            object.__setattr__(self, "updated_at", self.registered_at)
        if self.last_heartbeat_at is None:
            object.__setattr__(self, "last_heartbeat_at", self.registered_at)
        if self.last_progress_at is None:
            object.__setattr__(self, "last_progress_at", self.registered_at)

    def with_status(self, status: WorkerStatus) -> "WorkerNode":
        now = _now()
        return replace(self, status=status, updated_at=now, last_heartbeat_at=now)

    def with_progress(self) -> "WorkerNode":
        now = _now()
        return replace(self, updated_at=now, last_heartbeat_at=now, last_progress_at=now)


class WorkerRegistry(Protocol):
    async def register(self, node: WorkerNode) -> None: ...
    async def get(self, worker_id: str) -> WorkerNode | None: ...
    async def find_by_capability(self, capability: str) -> list[WorkerNode]: ...
    async def connect(self, source_id: str, target_id: str, relation: WorkerRelation) -> None: ...
    async def relations(self, worker_id: str) -> list[tuple[str, WorkerRelation]]: ...
    async def set_status(self, worker_id: str, status: WorkerStatus) -> WorkerNode: ...
    async def mark_progress(self, worker_id: str) -> WorkerNode: ...


class InMemoryWorkerRegistry:
    def __init__(self) -> None:
        self._nodes: dict[str, WorkerNode] = {}
        self._relations: dict[str, set[tuple[str, WorkerRelation]]] = {}

    async def register(self, node: WorkerNode) -> None:
        self._nodes[node.worker_id] = node

    async def get(self, worker_id: str) -> WorkerNode | None:
        return self._nodes.get(worker_id)

    async def find_by_capability(self, capability: str) -> list[WorkerNode]:
        return sorted(
            (node for node in self._nodes.values() if capability in node.capabilities),
            key=lambda node: node.worker_id,
        )

    async def connect(self, source_id: str, target_id: str, relation: WorkerRelation) -> None:
        if source_id not in self._nodes or target_id not in self._nodes:
            raise KeyError("both workers must be registered before connecting them")
        self._relations.setdefault(source_id, set()).add((target_id, relation))

    async def relations(self, worker_id: str) -> list[tuple[str, WorkerRelation]]:
        return sorted(self._relations.get(worker_id, ()), key=lambda edge: (edge[0], edge[1].value))

    async def set_status(self, worker_id: str, status: WorkerStatus) -> WorkerNode:
        node = self._require(worker_id).with_status(status)
        self._nodes[worker_id] = node
        return node

    async def mark_progress(self, worker_id: str) -> WorkerNode:
        node = self._require(worker_id).with_progress()
        self._nodes[worker_id] = node
        return node

    def _require(self, worker_id: str) -> WorkerNode:
        node = self._nodes.get(worker_id)
        if node is None:
            raise KeyError(worker_id)
        return node


def _node_payload(node: WorkerNode) -> str:
    return json.dumps(
        {
            "worker_id": node.worker_id,
            "capabilities": node.capabilities,
            "endpoint": node.endpoint,
            "status": node.status.value,
            "registered_at": node.registered_at.isoformat(),
            "updated_at": node.updated_at.isoformat(),
            "last_heartbeat_at": node.last_heartbeat_at.isoformat(),
            "last_progress_at": node.last_progress_at.isoformat(),
        },
        separators=(",", ":"),
        sort_keys=True,
    )


def _load_node(raw) -> WorkerNode | None:
    if raw is None:
        return None
    payload = json.loads(_decode(raw))
    return WorkerNode(
        worker_id=payload["worker_id"],
        capabilities=tuple(payload["capabilities"]),
        endpoint=payload["endpoint"],
        status=WorkerStatus(payload["status"]),
        registered_at=datetime.fromisoformat(payload["registered_at"]),
        updated_at=datetime.fromisoformat(payload["updated_at"]),
        last_heartbeat_at=datetime.fromisoformat(payload["last_heartbeat_at"]),
        last_progress_at=datetime.fromisoformat(payload["last_progress_at"]),
    )


class ValkeyWorkerRegistry:
    """Durable worker graph. Valkey errors propagate; there is no unsafe fallback."""

    PREFIX = "brain:workers"
    INDEX_KEY = f"{PREFIX}:index"

    def __init__(self, client) -> None:
        self.client = client

    def _node_key(self, worker_id: str) -> str:
        return f"{self.PREFIX}:node:{_hash(worker_id)}"

    def _relations_key(self, worker_id: str) -> str:
        return f"{self.PREFIX}:relations:{_hash(worker_id)}"

    async def register(self, node: WorkerNode) -> None:
        await self.client.set(self._node_key(node.worker_id), _node_payload(node))
        await self.client.sadd(self.INDEX_KEY, node.worker_id)

    async def get(self, worker_id: str) -> WorkerNode | None:
        return _load_node(await self.client.get(self._node_key(worker_id)))

    async def _all_nodes(self) -> list[WorkerNode]:
        worker_ids = sorted(_decode(value) for value in await self.client.smembers(self.INDEX_KEY))
        result: list[WorkerNode] = []
        for worker_id in worker_ids:
            node = await self.get(worker_id)
            if node is not None:
                result.append(node)
        return result

    async def find_by_capability(self, capability: str) -> list[WorkerNode]:
        return [node for node in await self._all_nodes() if capability in node.capabilities]

    async def connect(self, source_id: str, target_id: str, relation: WorkerRelation) -> None:
        if await self.get(source_id) is None or await self.get(target_id) is None:
            raise KeyError("both workers must be registered before connecting them")
        await self.client.sadd(
            self._relations_key(source_id),
            json.dumps([target_id, relation.value], separators=(",", ":")),
        )

    async def relations(self, worker_id: str) -> list[tuple[str, WorkerRelation]]:
        raw_edges = await self.client.smembers(self._relations_key(worker_id))
        edges = []
        for raw in raw_edges:
            target_id, relation = json.loads(_decode(raw))
            edges.append((target_id, WorkerRelation(relation)))
        return sorted(edges, key=lambda edge: (edge[0], edge[1].value))

    async def set_status(self, worker_id: str, status: WorkerStatus) -> WorkerNode:
        node = await self.get(worker_id)
        if node is None:
            raise KeyError(worker_id)
        node = node.with_status(status)
        await self.register(node)
        return node

    async def mark_progress(self, worker_id: str) -> WorkerNode:
        node = await self.get(worker_id)
        if node is None:
            raise KeyError(worker_id)
        node = node.with_progress()
        await self.register(node)
        return node
