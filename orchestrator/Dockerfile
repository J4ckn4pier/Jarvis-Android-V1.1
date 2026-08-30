FROM python:3.12-slim
WORKDIR /app
COPY pyproject.toml ./
COPY jarvis_orchestrator ./jarvis_orchestrator
RUN pip install --no-cache-dir . \
    && groupadd --system jarvis \
    && useradd --system --gid jarvis --home-dir /app --no-create-home jarvis
ENV PYTHONUNBUFFERED=1
EXPOSE 8000
USER jarvis
CMD ["uvicorn", "jarvis_orchestrator.app:app", "--host", "0.0.0.0", "--port", "8000"]
