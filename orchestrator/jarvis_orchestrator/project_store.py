"""Durable, owner-isolated storage for management-plane projects and tasks."""

from __future__ import annotations

import hashlib
import json
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from typing import Protocol

from .management import Project, ProjectState, Task, TaskState


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _hash(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def _decode(raw):
    if isinstance(raw, bytes):
        return raw.decode()
    return raw


def _iso(value: datetime | None) -> str | None:
    return None if value is None else value.isoformat()


def _dt(value: str | None) -> datetime | None:
    return None if value is None else datetime.fromisoformat(value)


def _project_payload(project: Project) -> str:
    payload = asdict(project)
    payload["state"] = project.state.value
    for field in ("deadline", "created_at", "updated_at", "last_progress_at"):
        payload[field] = _iso(payload[field])
    return json.dumps(payload, separators=(",", ":"), sort_keys=True)


def _task_payload(task: Task) -> str:
    payload = asdict(task)
    payload["state"] = task.state.value
    for field in ("deadline", "created_at", "updated_at", "last_progress_at"):
        payload[field] = _iso(payload[field])
    return json.dumps(payload, separators=(",", ":"), sort_keys=True)


def _load_project(raw) -> Project | None:
    if raw is None:
        return None
    payload = json.loads(_decode(raw))
    return Project(
        project_id=payload["project_id"],
        owner_id=payload["owner_id"],
        session_id=payload["session_id"],
        goal=payload["goal"],
        constraints=tuple(payload["constraints"]),
        acceptance_criteria=tuple(payload["acceptance_criteria"]),
        deadline=_dt(payload["deadline"]),
        state=ProjectState(payload["state"]),
        created_at=_dt(payload["created_at"]),
        updated_at=_dt(payload["updated_at"]),
        last_progress_at=_dt(payload["last_progress_at"]),
    )


def _load_task(raw) -> Task | None:
    if raw is None:
        return None
    payload = json.loads(_decode(raw))
    return Task(
        task_id=payload["task_id"],
        project_id=payload["project_id"],
        goal=payload["goal"],
        constraints=tuple(payload["constraints"]),
        acceptance_criteria=tuple(payload["acceptance_criteria"]),
        dependencies=tuple(payload["dependencies"]),
        required_capabilities=tuple(payload["required_capabilities"]),
        assigned_workers=tuple(payload["assigned_workers"]),
        deadline=_dt(payload["deadline"]),
        state=TaskState(payload["state"]),
        created_at=_dt(payload["created_at"]),
        updated_at=_dt(payload["updated_at"]),
        last_progress_at=_dt(payload["last_progress_at"]),
    )


@dataclass(frozen=True, slots=True)
class ProjectEvent:
    owner_id: str
    project_id: str
    kind: str
    task_id: str | None = None
    timestamp: datetime = None  # type: ignore[assignment]

    def __post_init__(self) -> None:
        if self.timestamp is None:
            object.__setattr__(self, "timestamp", _now())


class ProjectStore(Protocol):
    async def save_project(self, project: Project, event: str = "project.saved") -> None: ...
    async def get_project(self, owner_id: str, project_id: str) -> Project | None: ...
    async def save_task(self, owner_id: str, task: Task, event: str = "task.saved") -> None: ...
    async def list_tasks(self, owner_id: str, project_id: str) -> list[Task]: ...
    async def events(self, owner_id: str, project_id: str) -> list[ProjectEvent]: ...


class InMemoryProjectStore:
    """Deterministic zero-infrastructure implementation for unit tests/dev."""

    def __init__(self) -> None:
        self._projects: dict[tuple[str, str], Project] = {}
        self._tasks: dict[tuple[str, str, str], Task] = {}
        self._events: dict[tuple[str, str], list[ProjectEvent]] = {}

    async def save_project(self, project: Project, event: str = "project.saved") -> None:
        key = (project.owner_id, project.project_id)
        self._projects[key] = project
        self._events.setdefault(key, []).append(ProjectEvent(project.owner_id, project.project_id, event))

    async def get_project(self, owner_id: str, project_id: str) -> Project | None:
        return self._projects.get((owner_id, project_id))

    async def save_task(self, owner_id: str, task: Task, event: str = "task.saved") -> None:
        project_key = (owner_id, task.project_id)
        self._tasks[(owner_id, task.project_id, task.task_id)] = task
        self._events.setdefault(project_key, []).append(
            ProjectEvent(owner_id, task.project_id, event, task.task_id)
        )

    async def list_tasks(self, owner_id: str, project_id: str) -> list[Task]:
        tasks = [
            task
            for (owner, project, _), task in self._tasks.items()
            if owner == owner_id and project == project_id
        ]
        return sorted(tasks, key=lambda task: task.task_id)

    async def events(self, owner_id: str, project_id: str) -> list[ProjectEvent]:
        return list(self._events.get((owner_id, project_id), ()))


class ValkeyProjectStore:
    """Production project/task snapshots plus append-only transition events in Valkey.

    This class intentionally has no in-process fallback. If configured Valkey is
    unavailable, redis-py's exception propagates so callers cannot unknowingly
    split durable project state across process-local memory.
    """

    PREFIX = "brain:management"

    def __init__(self, client) -> None:
        self.client = client

    def _scope(self, owner_id: str, project_id: str) -> str:
        return f"{self.PREFIX}:{_hash(owner_id)}:{_hash(project_id)}"

    def _project_key(self, owner_id: str, project_id: str) -> str:
        return f"{self._scope(owner_id, project_id)}:project"

    def _task_key(self, owner_id: str, project_id: str, task_id: str) -> str:
        return f"{self._scope(owner_id, project_id)}:task:{_hash(task_id)}"

    def _task_index(self, owner_id: str, project_id: str) -> str:
        return f"{self._scope(owner_id, project_id)}:tasks"

    def _event_stream(self, owner_id: str, project_id: str) -> str:
        return f"{self._scope(owner_id, project_id)}:events"

    async def _append_event(self, event: ProjectEvent) -> None:
        payload = json.dumps(
            {
                "owner_id": event.owner_id,
                "project_id": event.project_id,
                "kind": event.kind,
                "task_id": event.task_id,
                "timestamp": event.timestamp.isoformat(),
            },
            separators=(",", ":"),
            sort_keys=True,
        )
        await self.client.xadd(self._event_stream(event.owner_id, event.project_id), {"event": payload})

    async def save_project(self, project: Project, event: str = "project.saved") -> None:
        await self.client.set(self._project_key(project.owner_id, project.project_id), _project_payload(project))
        await self._append_event(ProjectEvent(project.owner_id, project.project_id, event))

    async def get_project(self, owner_id: str, project_id: str) -> Project | None:
        return _load_project(await self.client.get(self._project_key(owner_id, project_id)))

    async def save_task(self, owner_id: str, task: Task, event: str = "task.saved") -> None:
        await self.client.set(self._task_key(owner_id, task.project_id, task.task_id), _task_payload(task))
        await self.client.sadd(self._task_index(owner_id, task.project_id), task.task_id)
        await self._append_event(ProjectEvent(owner_id, task.project_id, event, task.task_id))

    async def list_tasks(self, owner_id: str, project_id: str) -> list[Task]:
        task_ids = sorted(_decode(value) for value in await self.client.smembers(self._task_index(owner_id, project_id)))
        tasks: list[Task] = []
        for task_id in task_ids:
            task = _load_task(await self.client.get(self._task_key(owner_id, project_id, task_id)))
            if task is not None:
                tasks.append(task)
        return tasks

    async def events(self, owner_id: str, project_id: str) -> list[ProjectEvent]:
        rows = await self.client.xrange(self._event_stream(owner_id, project_id))
        result: list[ProjectEvent] = []
        for _, fields in rows:
            raw = fields.get(b"event") if b"event" in fields else fields.get("event")
            if raw is None:
                continue
            payload = json.loads(_decode(raw))
            result.append(
                ProjectEvent(
                    owner_id=payload["owner_id"],
                    project_id=payload["project_id"],
                    kind=payload["kind"],
                    task_id=payload["task_id"],
                    timestamp=datetime.fromisoformat(payload["timestamp"]),
                )
            )
        return result
