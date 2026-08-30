import pytest

from jarvis_orchestrator.preferences import (
    InMemoryPreferenceStore,
    PreferenceEngine,
    PreferenceEvidence,
    PreferenceRecord,
)


@pytest.mark.asyncio
async def test_ambiguous_choice_is_tentative_then_repeated_success_becomes_default():
    engine = PreferenceEngine(InMemoryPreferenceStore())

    first = await engine.observe_choice(
        owner_id="owner-a",
        subject="restaurant",
        relation="prefers_cuisine",
        target="thai",
        context="dinner",
        evidence_source="goal-1",
        successful=True,
    )
    assert first.explicit is False
    assert first.strength < engine.DEFAULT_THRESHOLD
    assert await engine.default_for("owner-a", "restaurant", "prefers_cuisine", "dinner") is None

    await engine.observe_choice("owner-a", "restaurant", "prefers_cuisine", "thai", "dinner", "goal-2", True)
    strengthened = await engine.observe_choice("owner-a", "restaurant", "prefers_cuisine", "thai", "dinner", "goal-3", True)

    assert strengthened.strength >= engine.DEFAULT_THRESHOLD
    assert (await engine.default_for("owner-a", "restaurant", "prefers_cuisine", "dinner")).target == "thai"


@pytest.mark.asyncio
async def test_explicit_always_promotes_immediately_and_correction_replaces_default():
    engine = PreferenceEngine(InMemoryPreferenceStore())
    promoted = await engine.set_explicit(
        "owner-a",
        "assistant",
        "response_style",
        "brief",
        context="work",
        evidence_source="user:always keep work updates brief",
    )
    assert promoted.explicit is True
    assert promoted.strength == 1.0
    assert promoted.confidence == 1.0

    corrected = await engine.correct(
        "owner-a",
        "assistant",
        "response_style",
        old_target="brief",
        new_target="detailed",
        context="work",
        evidence_source="user:correction",
    )

    assert corrected.target == "detailed"
    assert corrected.explicit is True
    assert (await engine.default_for("owner-a", "assistant", "response_style", "work")).target == "detailed"
    old = await engine.get("owner-a", "assistant", "response_style", "brief", "work")
    assert old.strength < engine.DEFAULT_THRESHOLD


@pytest.mark.asyncio
async def test_sensitive_permission_is_never_inferred():
    engine = PreferenceEngine(InMemoryPreferenceStore())

    with pytest.raises(ValueError, match="sensitive permission"):
        await engine.observe_choice(
            "owner-a",
            "permissions",
            "allow_delete",
            "true",
            "files",
            "inferred-from-behavior",
            True,
            sensitive_permission=True,
        )

    explicit = await engine.set_explicit(
        "owner-a",
        "permissions",
        "allow_archive",
        "true",
        context="files",
        evidence_source="user:explicit",
        sensitive_permission=True,
    )
    assert explicit.explicit is True


@pytest.mark.asyncio
async def test_preference_export_has_stable_graph_mirror_shape_and_owner_isolation():
    store = InMemoryPreferenceStore()
    engine = PreferenceEngine(store)
    await engine.observe_choice("owner-a", "food", "likes", "ramen", "dinner", "goal-1", True)
    await engine.observe_choice("owner-b", "food", "likes", "pizza", "dinner", "goal-2", True)

    exported = await engine.export_graph("owner-a")

    assert exported == [
        {
            "subject": "food",
            "relation": "likes",
            "target": "ramen",
            "context": "dinner",
            "strength": 0.4,
            "confidence": 0.35,
            "evidence_source": "goal-1",
            "explicit": False,
            "sensitive_permission": False,
            "updated_at": exported[0]["updated_at"],
        }
    ]
