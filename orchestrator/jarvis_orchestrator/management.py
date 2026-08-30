"""Provider-neutral management-plane domain contracts for JARVIS.

The models in this module deliberately contain no Agent Zero, LLM, transport, or
storage dependencies.  They are immutable value objects so later durable stores
can persist/replay exact state transitions without callers mutating shared state
behind the store's back.
"""

from __future__ import annotations

from dataclasses import dataclass, field, replace
from datetime import datetime, timezone
from enum import Enum
from typing import Iterable


def _now() -> datetime:
    return datetime.now(timezone.utc)


class ProjectState(str, Enum):
    """Lifecycle of a user-visible JARVIS project."""

    PENDING = "pending"
    ACTIVE = "active"
    BLOCKED = "blocked"
    VERIFYING = "verifying"
    COMPLETE = "complete"
    FAILED = "failed"
    CANCELLED = "cancelled"


class TaskState(str, Enum):
    """Lifecycle of one node in a project task graph."""

    PENDING = "pending"
    ASSIGNED = "assigned"
    RUNNING = "running"
    BLOCKED = "blocked"
    VERIFYING = "verifying"
    COMPLETE = "complete"
    FAILED = "failed"
    CANCELLED = "cancelled"


def _dedupe(values: Iterable[str]) -> tuple[str, ...]:
    """Return values in first-seen order with duplicates removed."""

    return tuple(dict.fromkeys(values))


@dataclass(frozen=True, slots=True)
class Project:
    """Persistent contract for a long-running goal owned by one JARVIS user."""

    project_id: str
    owner_id: str
    session_id: str
    goal: str
    constraints: tuple[str, ...] = ()
    acceptance_criteria: tuple[str, ...] = ()
    deadline: datetime | None = None
    state: ProjectState = ProjectState.PENDING
    created_at: datetime = field(default_factory=_now)
    updated_at: datetime | None = None
    last_progress_at: datetime | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "constraints", tuple(self.constraints))
        object.__setattr__(self, "acceptance_criteria", tuple(self.acceptance_criteria))
        if self.updated_at is None:
            object.__setattr__(self, "updated_at", self.created_at)
        if self.last_progress_at is None:
            object.__setattr__(self, "last_progress_at", self.created_at)

    def transition(self, state: ProjectState, *, progressed: bool = True) -> "Project":
        """Return a new project snapshot with a lifecycle transition recorded."""

        now = _now()
        return replace(
            self,
            state=state,
            updated_at=now,
            last_progress_at=now if progressed else self.last_progress_at,
        )

    def mark_progress(self) -> "Project":
        """Record meaningful project progress without changing lifecycle state."""

        now = _now()
        return replace(self, updated_at=now, last_progress_at=now)


@dataclass(frozen=True, slots=True)
class Task:
    """Persistent, graph-capable unit of work within a project."""

    task_id: str
    project_id: str
    goal: str
    constraints: tuple[str, ...] = ()
    acceptance_criteria: tuple[str, ...] = ()
    dependencies: tuple[str, ...] = ()
    required_capabilities: tuple[str, ...] = ()
    assigned_workers: tuple[str, ...] = ()
    deadline: datetime | None = None
    state: TaskState = TaskState.PENDING
    created_at: datetime = field(default_factory=_now)
    updated_at: datetime | None = None
    last_progress_at: datetime | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "constraints", tuple(self.constraints))
        object.__setattr__(self, "acceptance_criteria", tuple(self.acceptance_criteria))
        object.__setattr__(self, "dependencies", _dedupe(self.dependencies))
        object.__setattr__(self, "required_capabilities", _dedupe(self.required_capabilities))
        object.__setattr__(self, "assigned_workers", _dedupe(self.assigned_workers))
        if self.updated_at is None:
            object.__setattr__(self, "updated_at", self.created_at)
        if self.last_progress_at is None:
            object.__setattr__(self, "last_progress_at", self.created_at)

    def assign(self, worker_id: str) -> "Task":
        """Assign a worker without duplicating graph edges or mutating this snapshot."""

        workers = _dedupe((*self.assigned_workers, worker_id))
        if workers == self.assigned_workers and self.state is TaskState.ASSIGNED:
            return self
        now = _now()
        return replace(
            self,
            assigned_workers=workers,
            state=TaskState.ASSIGNED,
            updated_at=now,
            last_progress_at=now,
        )

    def transition(self, state: TaskState, *, progressed: bool = True) -> "Task":
        """Return a new task snapshot with a lifecycle transition recorded."""

        now = _now()
        return replace(
            self,
            state=state,
            updated_at=now,
            last_progress_at=now if progressed else self.last_progress_at,
        )

    def mark_progress(self) -> "Task":
        """Record meaningful worker/task progress without changing lifecycle state."""

        now = _now()
        return replace(self, updated_at=now, last_progress_at=now)
