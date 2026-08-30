from __future__ import annotations

from pathlib import Path

from bootstrap import compute_agent_zero_api_key, ensure_env


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


def test_compose_passes_generated_persistent_identity_to_agent_zero():
    compose = Path("compose.yaml").read_text()
    assert "A0_PERSISTENT_RUNTIME_ID: ${AGENT_ZERO_RUNTIME_ID:-}" in compose
