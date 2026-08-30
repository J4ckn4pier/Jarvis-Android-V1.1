from __future__ import annotations

import pytest

from jarvis_orchestrator.runtime import InMemoryAgentContextStore, build_runtime


def _agent_zero_env(monkeypatch) -> None:
    monkeypatch.setenv("JARVIS_RUNTIME", "agent-zero")
    monkeypatch.setenv("AGENT_ZERO_URL", "http://agent-zero:50001")
    monkeypatch.setenv("AGENT_ZERO_API_KEY", "secret")


def test_runtime_factory_rejects_non_http_agent_zero_url(monkeypatch):
    _agent_zero_env(monkeypatch)
    monkeypatch.setenv("AGENT_ZERO_URL", "file:///tmp/agent-zero")

    with pytest.raises(RuntimeError, match="AGENT_ZERO_URL must use http or https"):
        build_runtime(InMemoryAgentContextStore())


def test_runtime_factory_rejects_non_integer_lifetime(monkeypatch):
    _agent_zero_env(monkeypatch)
    monkeypatch.setenv("AGENT_ZERO_LIFETIME_HOURS", "forever")

    with pytest.raises(RuntimeError, match="AGENT_ZERO_LIFETIME_HOURS must be an integer"):
        build_runtime(InMemoryAgentContextStore())


def test_runtime_factory_rejects_non_positive_lifetime(monkeypatch):
    _agent_zero_env(monkeypatch)
    monkeypatch.setenv("AGENT_ZERO_LIFETIME_HOURS", "0")

    with pytest.raises(RuntimeError, match="AGENT_ZERO_LIFETIME_HOURS must be greater than zero"):
        build_runtime(InMemoryAgentContextStore())
