import asyncio

import pytest

from jarvis_orchestrator.core import (
    EchoRuntime,
    IdempotencyConflict,
    InMemoryEventBus,
    InMemoryIdempotencyStore,
    Orchestrator,
)


async def test_submit_broadcasts_ordered_state_and_returns_result():
    bus = InMemoryEventBus()
    orchestrator = Orchestrator(bus, EchoRuntime())
    seen = []

    async def listen():
        async for event in bus.subscribe():
            seen.append(event)
            if event.active_layer == "IDLE":
                return

    listener = asyncio.create_task(listen())
    await asyncio.sleep(0)
    result = await orchestrator.submit("hello", "same-session")
    await listener

    assert result["session_id"] == "same-session"
    assert result["response"] == "JARVIS received: hello"
    assert [e.active_layer for e in seen] == ["PREFRONTAL", "AGENT_OPS", "LANGUAGE", "IDLE"]
    assert len({e.task_id for e in seen}) == 1


async def test_same_session_commands_are_serialized():
    class Runtime:
        def __init__(self):
            self.active = 0
            self.max_active = 0

        async def execute(self, text, session_id):
            self.active += 1
            self.max_active = max(self.max_active, self.active)
            await asyncio.sleep(0.01)
            self.active -= 1
            return text

    runtime = Runtime()
    orchestrator = Orchestrator(InMemoryEventBus(), runtime)
    await asyncio.gather(
        orchestrator.submit("one", "primary"),
        orchestrator.submit("two", "primary"),
    )
    assert runtime.max_active == 1


async def test_history_recovers_events_after_client_was_offline():
    bus = InMemoryEventBus()
    orchestrator = Orchestrator(bus, EchoRuntime())

    first = await orchestrator.submit("offline command", "phone")
    recovered = await bus.history("phone")

    assert len(recovered) == 4
    assert [event.sequence for event in recovered] == [1, 2, 3, 4]
    assert {event.task_id for event in recovered} == {first["task_id"]}
    assert recovered[-1].active_layer == "IDLE"


async def test_history_is_session_scoped_and_limit_applies():
    bus = InMemoryEventBus()
    orchestrator = Orchestrator(bus, EchoRuntime())
    await orchestrator.submit("phone work", "phone")
    await orchestrator.submit("desktop work", "desktop")

    phone = await bus.history("phone", limit=2)
    desktop = await bus.history("desktop")

    assert len(phone) == 2
    assert all(event.session_id == "phone" for event in phone)
    assert len(desktop) == 4
    assert all(event.session_id == "desktop" for event in desktop)


async def test_history_can_page_forward_after_last_seen_event():
    bus = InMemoryEventBus()
    orchestrator = Orchestrator(bus, EchoRuntime())
    first = await orchestrator.submit("first", "phone")
    await orchestrator.submit("second", "phone")

    after = f"{first['task_id']}:4"
    recovered = await bus.history("phone", limit=4, after_event_id=after)

    assert len(recovered) == 4
    assert all(event.task_id != first["task_id"] for event in recovered)
    assert [event.sequence for event in recovered] == [1, 2, 3, 4]


async def test_retry_with_same_request_id_executes_runtime_once():
    class Runtime:
        def __init__(self):
            self.calls = 0

        async def execute(self, text, session_id):
            self.calls += 1
            return f"answer:{text}"

    runtime = Runtime()
    orchestrator = Orchestrator(
        InMemoryEventBus(),
        runtime,
        idempotency=InMemoryIdempotencyStore(),
    )

    first = await orchestrator.submit("turn on lights", "phone", request_id="mobile-123")
    retry = await orchestrator.submit("turn on lights", "phone", request_id="mobile-123")

    assert runtime.calls == 1
    assert retry == first
    assert first["request_id"] == "mobile-123"


async def test_request_id_reuse_with_different_command_is_rejected():
    orchestrator = Orchestrator(
        InMemoryEventBus(),
        EchoRuntime(),
        idempotency=InMemoryIdempotencyStore(),
    )
    await orchestrator.submit("first action", "phone", request_id="same-id")

    with pytest.raises(IdempotencyConflict):
        await orchestrator.submit("different action", "phone", request_id="same-id")


async def test_same_request_id_is_independent_between_sessions():
    orchestrator = Orchestrator(
        InMemoryEventBus(),
        EchoRuntime(),
        idempotency=InMemoryIdempotencyStore(),
    )

    phone = await orchestrator.submit("phone command", "phone", request_id="req-1")
    desktop = await orchestrator.submit("desktop command", "desktop", request_id="req-1")

    assert phone["task_id"] != desktop["task_id"]
    assert phone["response"] != desktop["response"]
