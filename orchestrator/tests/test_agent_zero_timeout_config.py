from __future__ import annotations

from pathlib import Path

import pytest

from jarvis_orchestrator.runtime import AgentZeroRuntime, InMemoryAgentContextStore, build_runtime


def _configure_agent_zero(monkeypatch, timeout: str) -> None:
    monkeypatch.setenv("JARVIS_RUNTIME", "agent-zero")
    monkeypatch.setenv("AGENT_ZERO_URL", "http://agent-zero")
    monkeypatch.setenv("AGENT_ZERO_API_KEY", "secret")
    monkeypatch.setenv("AGENT_ZERO_TIMEOUT_SECONDS", timeout)


def test_runtime_factory_applies_configurable_agent_zero_inference_timeout(monkeypatch):
    _configure_agent_zero(monkeypatch, "45")

    runtime = build_runtime(InMemoryAgentContextStore())

    assert isinstance(runtime, AgentZeroRuntime)
    assert runtime.client.timeout.read == 45.0


def test_runtime_factory_rejects_nonpositive_agent_zero_inference_timeout(monkeypatch):
    _configure_agent_zero(monkeypatch, "0")

    with pytest.raises(RuntimeError, match="AGENT_ZERO_TIMEOUT_SECONDS"):
        build_runtime(InMemoryAgentContextStore())


def test_packaged_compose_forwards_runtime_and_session_lock_timeouts():
    compose = Path("compose.yaml").read_text()

    assert "AGENT_ZERO_TIMEOUT_SECONDS: ${AGENT_ZERO_TIMEOUT_SECONDS:-300}" in compose
    assert "JARVIS_SESSION_LOCK_TIMEOUT_SECONDS: ${JARVIS_SESSION_LOCK_TIMEOUT_SECONDS:-}" in compose
