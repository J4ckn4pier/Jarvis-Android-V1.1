from __future__ import annotations

import stat
from pathlib import Path

import pytest

from jarvis_orchestrator.bootstrap import compute_agent_zero_api_key, ensure_env


def test_agent_zero_api_key_matches_upstream_token_derivation():
    assert compute_agent_zero_api_key("runtime-test-id") == "M_865Q204X-D3UfF"


def test_bootstrap_creates_required_secrets_once(tmp_path: Path):
    env_path = tmp_path / ".env"

    first = ensure_env(env_path)
    second = ensure_env(env_path)

    assert first == second
    assert len(first["JARVIS_API_TOKEN"]) >= 32
    assert len(first["AGENT_ZERO_RUNTIME_ID"]) == 32
    assert first["AGENT_ZERO_API_KEY"] == compute_agent_zero_api_key(first["AGENT_ZERO_RUNTIME_ID"])

    text = env_path.read_text()
    assert f"JARVIS_API_TOKEN={first['JARVIS_API_TOKEN']}" in text
    assert f"AGENT_ZERO_RUNTIME_ID={first['AGENT_ZERO_RUNTIME_ID']}" in text
    assert f"AGENT_ZERO_API_KEY={first['AGENT_ZERO_API_KEY']}" in text


def test_bootstrap_selects_bundled_agent_zero_runtime_without_overwriting_existing_values(tmp_path: Path):
    env_path = tmp_path / ".env"
    env_path.write_text("AGENT_ZERO_LIFETIME_HOURS=48\n")

    values = ensure_env(env_path)

    assert values["JARVIS_RUNTIME"] == "agent-zero"
    assert values["AGENT_ZERO_URL"] == "http://agent-zero"
    assert values["AGENT_ZERO_LIFETIME_HOURS"] == "48"
    text = env_path.read_text()
    assert "JARVIS_RUNTIME=agent-zero" in text
    assert "AGENT_ZERO_URL=http://agent-zero" in text
    assert text.count("AGENT_ZERO_LIFETIME_HOURS=48") == 1


def test_bootstrap_rejects_existing_secret_with_edge_whitespace(tmp_path: Path):
    env_path = tmp_path / ".env"
    env_path.write_text("JARVIS_API_TOKEN= secret-token \n")

    with pytest.raises(ValueError, match="JARVIS_API_TOKEN must not have leading or trailing whitespace"):
        ensure_env(env_path)


def test_bootstrap_rejects_duplicate_owner_secret(tmp_path: Path):
    env_path = tmp_path / ".env"
    env_path.write_text("JARVIS_API_TOKEN=first-token\nJARVIS_API_TOKEN=second-token\n")

    with pytest.raises(ValueError, match="JARVIS_API_TOKEN must be configured only once"):
        ensure_env(env_path)


def test_bootstrap_locks_env_file_to_owner_only(tmp_path: Path):
    env_path = tmp_path / ".env"
    ensure_env(env_path)
    assert stat.S_IMODE(env_path.stat().st_mode) == 0o600


def test_compose_persists_generated_identity_in_agent_zero_own_env():
    compose = Path("compose.yaml").read_text()
    assert "A0_PERSISTENT_RUNTIME_ID: ${AGENT_ZERO_RUNTIME_ID:-}" in compose
    assert "AGENT_ZERO_RUNTIME_ID: ${AGENT_ZERO_RUNTIME_ID:-}" in compose
    assert "/a0/usr/.env" in compose
    assert "A0_PERSISTENT_RUNTIME_ID=$AGENT_ZERO_RUNTIME_ID" in compose


def test_generated_env_file_is_ignored_by_orchestrator_workstream():
    ignore = Path(".gitignore")
    assert ignore.exists(), "orchestrator must protect generated .env secrets"
    assert ".env" in {line.strip() for line in ignore.read_text().splitlines()}
