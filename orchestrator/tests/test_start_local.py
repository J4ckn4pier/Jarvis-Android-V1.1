from __future__ import annotations

from pathlib import Path


def test_local_start_script_bootstraps_secrets_model_and_full_stack():
    script = Path("start-local.sh")
    assert script.exists(), "one-command local prototype launcher must be packaged"
    text = script.read_text()

    assert "set -eu" in text
    assert "python3 bootstrap.py" in text
    assert "docker compose --profile local-ai up -d ollama" in text
    assert "docker compose --profile local-ai run --rm ollama-model-bootstrap" in text
    assert "docker compose --profile agent-zero --profile local-ai up -d --build" in text
    assert "curl" in text and "/ready" in text
