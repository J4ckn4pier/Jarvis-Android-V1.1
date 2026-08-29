FROM python:3.12-slim
WORKDIR /app
COPY pyproject.toml ./
COPY jarvis_orchestrator ./jarvis_orchestrator
RUN pip install --no-cache-dir .
ENV PYTHONUNBUFFERED=1
EXPOSE 8000
CMD ["uvicorn", "jarvis_orchestrator.app:app", "--host", "0.0.0.0", "--port", "8000"]
