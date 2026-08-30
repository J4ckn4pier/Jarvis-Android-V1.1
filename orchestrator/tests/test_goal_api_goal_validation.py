from __future__ import annotations

import pytest
from pydantic import ValidationError

from jarvis_orchestrator.goal_api import GoalSubmission


def test_goal_submission_rejects_whitespace_only_goal_without_normalizing_valid_text():
    with pytest.raises(ValidationError):
        GoalSubmission(goal="   \t\n")

    body = GoalSubmission(goal="  Compare two restaurants exactly as I phrased this.  ")
    assert body.goal == "  Compare two restaurants exactly as I phrased this.  "
