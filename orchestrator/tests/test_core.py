import asyncio

from jarvis_orchestrator.core import EchoRuntime, InMemoryEventBus, Orchestrator


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
