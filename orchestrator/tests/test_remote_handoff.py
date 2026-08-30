from pathlib import Path


def test_remote_bind_is_opt_in_and_loopback_remains_default():
    root = Path(__file__).parents[1]
    compose = (root / "compose.yaml").read_text(encoding="utf-8")
    deployment = (root / "DEPLOYMENT.md").read_text(encoding="utf-8")

    assert '"${JARVIS_BIND_ADDRESS:-127.0.0.1}:8000:8000"' in compose
    assert "JARVIS_BIND_ADDRESS=0.0.0.0" in deployment
    assert "Authorization: Bearer <token>" in deployment
    assert "Valkey, Agent Zero, and Ollama remain private" in deployment
    assert "TLS" in deployment
