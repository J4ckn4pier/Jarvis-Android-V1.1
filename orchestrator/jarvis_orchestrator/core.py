from __future__ import annotations

import asyncio
import json
import time
import uuid
from dataclasses import asdict, dataclass
from typing import AsyncIterator, Protocol


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


class InMemoryEventBus:
    """Zero-infrastructure bus for development/tests; production swaps in ValkeyEventBus."""
    def __init__(self) -> None:
        self._subscribers: set[asyncio.Queue[BrainEvent]] = set()

    async def publish(self, event: BrainEvent) -> None:
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


class ValkeyEventBus:
    """Valkey uses the Redis wire protocol; redis-py is the MIT-licensed Python client."""
    CHANNEL = "brain:state"

    def __init__(self, client) -> None:
        self.client = client

    async def publish(self, event: BrainEvent) -> None:
        payload = json.dumps(asdict(event), separators=(",", ":"))
        await self.client.publish(self.CHANNEL, payload)
        await self.client.set(f"brain:session:{event.session_id}:latest", payload, ex=86400)

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


class AgentRuntime(Protocol):
    async def execute(self, text: str, session_id: str) -> str: ...


class EchoRuntime:
    """Runnable baseline runtime; replaced by Agent Zero adapter without changing clients."""
    async def execute(self, text: str, session_id: str) -> str:
        await asyncio.sleep(0)
        return f"JARVIS received: {text}"


class Orchestrator:
    """The one authoritative execution path used by phone, desktop, CLI, and future clients."""
    def __init__(self, bus: EventBus, runtime: AgentRuntime) -> None:
        self.bus = bus
        self.runtime = runtime
        self._session_locks: dict[str, asyncio.Lock] = {}

    async def submit(self, text: str, session_id: str) -> dict[str, str]:
        task_id = str(uuid.uuid4())
        lock = self._session_locks.setdefault(session_id, asyncio.Lock())
        async with lock:
            await self._emit(session_id, task_id, "PREFRONTAL", 32, "Routing request", 1)
            await self._emit(session_id, task_id, "AGENT_OPS", 96, "Dispatching worker", 2)
            try:
                result = await self.runtime.execute(text, session_id)
            except Exception as exc:
                await self._emit(session_id, task_id, "AGENT_OPS", 0, f"Failed: {type(exc).__name__}", 3)
                raise
            await self._emit(session_id, task_id, "LANGUAGE", 48, "Preparing response", 3)
            await self._emit(session_id, task_id, "IDLE", 0, "Complete", 4)
            return {"session_id": session_id, "task_id": task_id, "response": result}

    async def _emit(self, session_id: str, task_id: str, layer: str, neurons: int, status: str, sequence: int) -> None:
        await self.bus.publish(BrainEvent(session_id, task_id, layer, neurons, status, sequence, time.time()))
