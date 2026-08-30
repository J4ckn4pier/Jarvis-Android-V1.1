#!/bin/sh
set -eu

python3 bootstrap.py

echo "Starting local Ollama service..."
docker compose --profile local-ai up -d ollama

echo "Ensuring Qwen3-4B is available locally..."
docker compose --profile local-ai run --rm ollama-model-bootstrap

echo "Starting JARVIS Orchestrator, Agent Zero, Valkey, and local AI..."
docker compose --profile agent-zero --profile local-ai up -d --build

echo "Waiting for JARVIS readiness..."
attempt=1
while [ "$attempt" -le 60 ]; do
  if curl -fsS http://127.0.0.1:8000/ready >/tmp/jarvis-ready.json 2>/dev/null; then
    cat /tmp/jarvis-ready.json
    echo
    echo "JARVIS local prototype is ready."
    exit 0
  fi
  attempt=$((attempt + 1))
  sleep 2
done

echo "JARVIS did not become ready. Recent service status follows:" >&2
docker compose --profile agent-zero --profile local-ai ps >&2
exit 1
