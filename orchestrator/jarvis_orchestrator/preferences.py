"""Evolvable associative preference memory for the JARVIS management plane.

This module deliberately exposes a graph-shaped subject/relation/target contract
rather than coupling callers to a particular memory backend.  A future Obsidian
or GBrain mirror can consume the export shape without changing planner/runtime
callers.
"""

from __future__ import annotations

from dataclasses import dataclass, field, replace
from datetime import datetime, timezone
from typing import Protocol


def _now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass(frozen=True, slots=True)
class PreferenceEvidence:
    """One observation supporting or contradicting an associative preference."""

    source: str
    successful: bool
    observed_at: datetime = field(default_factory=_now)


@dataclass(frozen=True, slots=True)
class PreferenceRecord:
    """One owner-scoped subject → relation → target association."""

    subject: str
    relation: str
    target: str
    context: str
    strength: float
    confidence: float
    evidence_source: str
    explicit: bool
    sensitive_permission: bool = False
    updated_at: datetime = field(default_factory=_now)


class PreferenceStore(Protocol):
    async def put(self, owner_id: str, record: PreferenceRecord) -> None: ...

    async def get(
        self,
        owner_id: str,
        subject: str,
        relation: str,
        target: str,
        context: str,
    ) -> PreferenceRecord | None: ...

    async def list_for_owner(self, owner_id: str) -> list[PreferenceRecord]: ...


class InMemoryPreferenceStore:
    """Deterministic owner-isolated implementation for tests/local development."""

    def __init__(self) -> None:
        self._records: dict[tuple[str, str, str, str, str], PreferenceRecord] = {}

    @staticmethod
    def _key(
        owner_id: str,
        subject: str,
        relation: str,
        target: str,
        context: str,
    ) -> tuple[str, str, str, str, str]:
        return owner_id, subject, relation, target, context

    async def put(self, owner_id: str, record: PreferenceRecord) -> None:
        self._records[
            self._key(owner_id, record.subject, record.relation, record.target, record.context)
        ] = record

    async def get(
        self,
        owner_id: str,
        subject: str,
        relation: str,
        target: str,
        context: str,
    ) -> PreferenceRecord | None:
        return self._records.get(self._key(owner_id, subject, relation, target, context))

    async def list_for_owner(self, owner_id: str) -> list[PreferenceRecord]:
        records = [
            record
            for (owner, _, _, _, _), record in self._records.items()
            if owner == owner_id
        ]
        return sorted(
            records,
            key=lambda record: (record.subject, record.relation, record.context, record.target),
        )


class PreferenceEngine:
    """Strengthen successful associations while reserving sensitive choices to users."""

    DEFAULT_THRESHOLD = 0.7
    INITIAL_SUCCESS_STRENGTH = 0.4
    INITIAL_SUCCESS_CONFIDENCE = 0.35
    SUCCESS_INCREMENT = 0.2
    FAILURE_DECREMENT = 0.25

    def __init__(self, store: PreferenceStore) -> None:
        self.store = store

    async def get(
        self,
        owner_id: str,
        subject: str,
        relation: str,
        target: str,
        context: str,
    ) -> PreferenceRecord | None:
        return await self.store.get(owner_id, subject, relation, target, context)

    async def observe_choice(
        self,
        owner_id: str,
        subject: str,
        relation: str,
        target: str,
        context: str,
        evidence_source: str,
        successful: bool,
        sensitive_permission: bool = False,
    ) -> PreferenceRecord:
        if sensitive_permission:
            raise ValueError("sensitive permission may never be inferred")

        existing = await self.store.get(owner_id, subject, relation, target, context)
        if existing is None:
            if successful:
                strength = self.INITIAL_SUCCESS_STRENGTH
                confidence = self.INITIAL_SUCCESS_CONFIDENCE
            else:
                strength = 0.0
                confidence = 0.2
            record = PreferenceRecord(
                subject=subject,
                relation=relation,
                target=target,
                context=context,
                strength=strength,
                confidence=confidence,
                evidence_source=evidence_source,
                explicit=False,
                sensitive_permission=False,
            )
        else:
            delta = self.SUCCESS_INCREMENT if successful else -self.FAILURE_DECREMENT
            record = replace(
                existing,
                strength=round(max(0.0, min(1.0, existing.strength + delta)), 10),
                confidence=round(
                    max(0.0, min(1.0, existing.confidence + delta)), 10
                ),
                evidence_source=evidence_source,
                updated_at=_now(),
            )

        await self.store.put(owner_id, record)
        return record

    async def set_explicit(
        self,
        owner_id: str,
        subject: str,
        relation: str,
        target: str,
        *,
        context: str,
        evidence_source: str,
        sensitive_permission: bool = False,
    ) -> PreferenceRecord:
        record = PreferenceRecord(
            subject=subject,
            relation=relation,
            target=target,
            context=context,
            strength=1.0,
            confidence=1.0,
            evidence_source=evidence_source,
            explicit=True,
            sensitive_permission=sensitive_permission,
        )
        await self.store.put(owner_id, record)
        return record

    async def correct(
        self,
        owner_id: str,
        subject: str,
        relation: str,
        *,
        old_target: str,
        new_target: str,
        context: str,
        evidence_source: str,
        sensitive_permission: bool = False,
    ) -> PreferenceRecord:
        old = await self.store.get(owner_id, subject, relation, old_target, context)
        if old is not None:
            weakened = replace(
                old,
                strength=0.0,
                confidence=min(old.confidence, 0.25),
                evidence_source=evidence_source,
                updated_at=_now(),
            )
            await self.store.put(owner_id, weakened)

        return await self.set_explicit(
            owner_id,
            subject,
            relation,
            new_target,
            context=context,
            evidence_source=evidence_source,
            sensitive_permission=sensitive_permission,
        )

    async def default_for(
        self,
        owner_id: str,
        subject: str,
        relation: str,
        context: str,
    ) -> PreferenceRecord | None:
        candidates = [
            record
            for record in await self.store.list_for_owner(owner_id)
            if record.subject == subject
            and record.relation == relation
            and record.context == context
            and record.strength >= self.DEFAULT_THRESHOLD
        ]
        if not candidates:
            return None
        return max(
            candidates,
            key=lambda record: (
                record.explicit,
                record.strength,
                record.confidence,
                record.updated_at,
                record.target,
            ),
        )

    async def export_graph(self, owner_id: str) -> list[dict[str, object]]:
        """Return a stable backend-neutral mirror shape for graph memory backends."""

        return [
            {
                "subject": record.subject,
                "relation": record.relation,
                "target": record.target,
                "context": record.context,
                "strength": record.strength,
                "confidence": record.confidence,
                "evidence_source": record.evidence_source,
                "explicit": record.explicit,
                "sensitive_permission": record.sensitive_permission,
                "updated_at": record.updated_at.isoformat(),
            }
            for record in await self.store.list_for_owner(owner_id)
        ]
