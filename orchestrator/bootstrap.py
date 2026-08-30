from jarvis_orchestrator.bootstrap import ensure_env


if __name__ == "__main__":
    ensure_env()
    print("JARVIS prototype secrets are ready in .env")
