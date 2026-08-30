from __future__ import annotations

import asyncio
import hashlib
import json
import time
import uuid
from dataclasses import asdict, dataclass
from typing import AsyncContextManager, AsyncIterator, Protocol


@dataclass(slots=True)
class BrainEvent:
    session_id: str
    task_id: str
    active_layer: str
    neurons_firing: int
    agent_ops_status: str
    sequence: int
    timestamp: float


class EventBus(Protocol):
    async def publish(self, event: BrainEvent) -> None: ...
    async def subscribe(self) -> AsyncIterator[BrainEvent]: ...
    async def history(self, session_id: str, limit: int = 100) -> list[BrainEvent]: ...


class InMemoryEventBus:
    """Zero-infrastructure bus for development/tests; production swaps in ValkeyEventBus."""
    def __init__(self) -> None:
        self._subscribers: set[asyncio.Queue[BrainEvent]] = set()
        self._history: dict[str, list[BrainEvent]] = {}

    async def publish(self, event: BrainEvent) -> None:
        events = self._history.setdefault(event.session_id, [])
        events.append(event)
        if len(events) > 1000:
            del events[:-1000]
        for queue in tuple(self._subscribers):
            await queue.put(event)

    async def subscribe(self) -> AsyncIterator[BrainEvent]:
        queue: asyncio.Queue[BrainEvent] = asyncio.Queue()
        self._subscribers.add(queue)
        try:
            while True:
                yield await queue.get()
        finally:
            self._subscribers.discard(queue)

    async def history(self, session_id: str, limit: int = 100) -> list[BrainEvent]:
        return list(self._history.get(session_id, []))[-limit:]


class ValkeyEventBus:
    """Live Pub/Sub plus durable per-session Valkey Streams history."""
    CHANNEL = "brain:state"
    STREAM_PREFIX = "brain:events:"

    def __init__(self, client) -> None:
        self.client = client

    async def publish(self, event: BrainEvent) -> None:
        payload = json.dumps(asdict(event), separators=(",", ":"))
        stream = f"{self.STREAM_PREFIX}{event.session_id}"
        await self.client.xadd(stream, {"event": payload}, maxlen=10_000, approximate=True)
        await self.client.expire(stream, 604800)
        await self.client.set(f"brain:session:{event.session_id}:latest", payload, ex=86400)
        await self.client.publish(self.CHANNEL, payload)

    async def subscribe(self) -> AsyncIterator[BrainEvent]:
        pubsub = self.client.pubsub()
        await pubsub.subscribe(self.CHANNEL)
        try:
            async for message in pubsub.listen():
                if message.get("type") != "message":
                    continue
                raw = message["data"]
                if isinstance(raw, bytes):
                    raw = raw.decode()
                yield BrainEvent(**json.loads(raw))
        finally:
            await pubsub.unsubscribe(self.CHANNEL)
            await pubsub.aclose()

    async def history(self, session_id: str, limit: int = 100) -> list[BrainEvent]:
        rows = await self.client.xrevrange(f"{self.STREAM_PREFIX}{session_id}", count=limit)
        events: list[BrainEvent] = []
        for _, fields in reversed(rows):
            raw = fields.get(b"event") if b"event" in fields else fields.get("event")
            if isinstance(raw, bytes):
                raw = raw.decode()
            if raw:
                events.append(BrainEvent(**json.loads(raw)))
        return events


class AgentRuntime(Protocol):
    async def execute(self, text: str, session_id: str) -> str: ...


class EchoRuntime:
    """Runnable baseline runtime; replaced by Agent Zero adapter without changing clients."""
    async def execute(self, text: str, session_id: str) -> str:
        await asyncio.sleep(0)
        return f"JARVIS received: {text}"


class SessionLockManager(Protocol):
    def lock(self, session_id: str) -> AsyncContextManager[object]: ...


class InMemorySessionLockManager:
    """Single-process fallback used when Valkey is not configured."""
    def __init__(self) -> None:
        self._locks: dict[str, asyncio.Lock] = {}

    def lock(self, session_id: str) -> asyncio.Lock:
        return self._locks.setdefault(session_id, asyncio.Lock())


class ValkeySessionLockManager:
    """Cross-process per-session serialization using redis-py's async Lock."""
    KEY_PREFIX = "brain:session-lock:"

    def __init__(self, client, timeout_seconds: int = 300) -> None:
        self.client = client
        self.timeout_seconds = timeout_seconds

    def lock(self, session_id: str):
        return self.client.lock(
            f"{self.KEY_PREFIX}{session_id}",
            timeout=self.timeout_seconds,
            blocking_timeout=self.timeout_seconds,
        )


@dataclass(slots=True)
class IdempotencyRecord:
    fingerprint: str
    result: dict[str, str]


class IdempotencyConflict(Exception):
    """A client reused a request ID for a different command payload."""


class IdempotencyStore(Protocol):
    async def get(self, session_id: str, request_id: str) -> IdempotencyRecord | None: ...
    async def put(self, session_id: str, request_id: str, record: IdempotencyRecord) -> None: ...


class InMemoryIdempotencyStore:
    def __init__(self) -> None:
        self._records: dict[tuple[str, str], IdempotencyRecord] = {}

    async def get(self, session_id: str, request_id: str) -> IdempotencyRecord | None:
        return self._records.get((session_id, request_id))

    async def put(self, session_id: str, request_id: str, record: IdempotencyRecord) -> None:
        self._records[(session_id, request_id)] = record


class ValkeyIdempotencyStore:
    """Durable retry results shared across orchestrator workers for 24 hours."""
    KEY_PREFIX = "brain:idempotency:"

    def __init__(self, client, ttl_seconds: int = 86400) -> None:
        self.client = client
        self.ttl_seconds = ttl_seconds

    def _key(self, session_id: str, request_id: str) -> str:
        request_hash = hashlib.sha256(request_id.encode()).hexdigest()
        return f"{self.KEY_PREFIX}{session_id}:{request_hash}"

    async def get(self, session_id: str, request_id: str) -> IdempotencyRecord | None:
        raw = await self.client.get(self._key(session_id, request_id))
        if raw is None:
            return None
        if isinstance(raw, bytes):
            raw = raw.decode()
        payload = json.loads(raw)
        return IdempotencyRecord(
            fingerprint=str(payload["fingerprint"]),
            result={str(key): str(value) for key, value in payload["result"].items()},
        )

    async def put(self, session_id: str, request_id: str, record: IdempotencyRecord) -> None:
        payload = json.dumps(
            {"fingerprint": record.fingerprint, "result": record.result},
            separators=(",", ":"),
        )
        await self.client.set(
            self._key(session_id, request_id),
            payload,
            ex=self.ttl_seconds,
        )


def _command_fingerprint(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


class Orchestrator:
    """The one authoritative execution path used by phone, desktop, CLI, and future clients."""
    def __init__(
        self,
        bus: EventBus,
        runtime: AgentRuntime,
        session_locks: SessionLockManager | None = None,
        idempotency: IdempotencyStore | None = None,
    ) -> None:
        self.bus = bus
        self.runtime = runtime
        self.session_locks = session_locks or InMemorySessionLockManager()
        self.idempotency = idempotency

    async def submit(
        self,
        text: str,
        session_id: str,
        request_id: str | None = None,
    ) -> dict[str, str]:
        async with self.session_locks.lock(session_id):
            fingerprint = _command_fingerprint(text)
            if request_id and self.idempotency:
                previous = await self.idempotency.get(session_id, request_id)
                if previous is not None:
                    if previous.fingerprint != fingerprint:
                        raise IdempotencyConflict(
                            "request_id was already used for a different command"
                        )
                    return dict(previous.result)

            task_id = str(uuid.uuid4())
            await self._emit(session_id, task_id, "PREFRONTAL", 32, "Routing request", 1)
            await self._emit(session_id, task_id, "AGENT_OPS", 96, "Dispatching worker", 2)
            try:
                result = await self.runtime.execute(text, session_id)
            except Exception as exc:
                await self._emit(session_id, task_id, "AGENT_OPS", 0, f"Failed: {type(exc).__name__}", 3)
                raise
            await self._emit(session_id, task_id, "LANGUAGE", 48, "Preparing response", 3)
            await self._emit(session_id, task_id, "IDLE", 0, "Complete", 4)
            response = {"session_id": session_id, "task_id": task_id, "response": result}
            if request_id:
                response["request_id"] = request_id
                if self.idempotency:
                    await self.idempotency.put(
                        session_id,
                        request_id,
                        IdempotencyRecord(fingerprint=fingerprint, result=dict(response)),
                    )
            return response

    async def _emit(self, session_id: str, task_id: str, layer: str, neurons: int, status: str, sequence: int) -> None:
        await self.bus.publish(BrainEvent(session_id, task_id, layer, neurons, status, sequence, time.time()))
