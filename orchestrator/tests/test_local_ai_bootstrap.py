from __future__ import annotations

from pathlib import Path


def test_local_agent_zero_preset_is_packaged_for_ollama_qwen():
    preset = Path("agent-zero/model-presets.yaml")
    assert preset.exists(), "local Agent Zero preset must be packaged"

    text = preset.read_text()
    assert "name: Default" in text
    assert text.count("provider: ollama") >= 2
    assert text.count("name: qwen3:4b") >= 2
    assert text.count('api_base: "http://ollama:11434"') >= 2
    assert "provider: huggingface" in text
    assert "sentence-transformers/all-MiniLM-L6-v2" in text


def test_compose_bootstraps_model_preset_without_overwriting_existing_user_config():
    compose = Path("compose.yaml").read_text()

    assert "agent-zero-bootstrap:" in compose
    assert "[ ! -f /a0/usr/plugins/_model_config/presets.yaml ]" in compose
    assert "cp /bootstrap/model-presets.yaml /a0/usr/plugins/_model_config/presets.yaml" in compose
    assert "condition: service_completed_successfully" in compose


def test_compose_selects_agent_zero_tiny_local_profile():
    compose = Path("compose.yaml").read_text()
    assert "A0_SET_AGENT_PROFILE: tiny-local" in compose


def test_compose_can_download_qwen_model_without_manual_exec():
    compose = Path("compose.yaml").read_text()
    assert "ollama-model-bootstrap:" in compose
    assert "OLLAMA_HOST: http://ollama:11434" in compose
    assert "ollama pull qwen3:4b" in compose
    assert "until ollama list" in compose
