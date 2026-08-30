import asyncio
from contextlib import asynccontextmanager

from jarvis_orchestrator import app as app_module
from jarvis_orchestrator.core import (
    InMemoryEventBus,
    InMemorySessionLockManager,
    Orchestrator,
    ValkeySessionLockManager,
)


async def test_shared_lock_manager_serializes_same_session_across_orchestrators():
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
    locks = InMemorySessionLockManager()
    first = Orchestrator(InMemoryEventBus(), runtime, session_locks=locks)
    second = Orchestrator(InMemoryEventBus(), runtime, session_locks=locks)

    await asyncio.gather(
        first.submit("one", "primary"),
        second.submit("two", "primary"),
    )

    assert runtime.max_active == 1


async def test_valkey_lock_manager_uses_stable_per_session_lock_key():
    calls = []

    class FakeLock:
        async def __aenter__(self):
            calls.append("enter")

        async def __aexit__(self, exc_type, exc, tb):
            calls.append("exit")

    class FakeClient:
        def lock(self, name, *, timeout, blocking_timeout):
            calls.append((name, timeout, blocking_timeout))
            return FakeLock()

    manager = ValkeySessionLockManager(FakeClient(), timeout_seconds=120)

    async with manager.lock("phone-primary"):
        calls.append("inside")

    assert calls == [
        ("brain:session-lock:phone-primary", 120, 120),
        "enter",
        "inside",
        "exit",
    ]


def test_agent_zero_session_lock_outlives_inference_timeout(monkeypatch):
    monkeypatch.setenv("JARVIS_RUNTIME", "agent-zero")
    monkeypatch.setenv("AGENT_ZERO_TIMEOUT_SECONDS", "900")
    monkeypatch.delenv("JARVIS_SESSION_LOCK_TIMEOUT_SECONDS", raising=False)

    resolver = getattr(app_module, "_session_lock_timeout_seconds", lambda: None)

    assert resolver() == 960


def test_explicit_session_lock_cannot_expire_before_agent_zero_timeout(monkeypatch):
    monkeypatch.setenv("JARVIS_RUNTIME", "agent-zero")
    monkeypatch.setenv("AGENT_ZERO_TIMEOUT_SECONDS", "900")
    monkeypatch.setenv("JARVIS_SESSION_LOCK_TIMEOUT_SECONDS", "600")

    resolver = getattr(app_module, "_session_lock_timeout_seconds", lambda: None)

    try:
        resolver()
    except RuntimeError as exc:
        assert "session lock timeout" in str(exc).lower()
    else:
        raise AssertionError("short session lock timeout must be rejected")
