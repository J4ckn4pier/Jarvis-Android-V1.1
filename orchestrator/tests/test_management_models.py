from datetime import datetime, timezone

from jarvis_orchestrator.management import Project, ProjectState, Task, TaskState


def test_project_captures_long_running_goal_contract():
    project = Project(
        project_id="project-1",
        owner_id="charles",
        session_id="primary",
        goal="Build and verify the orchestrator management plane",
        constraints=("open mandatory core", "phone-primary"),
        acceptance_criteria=("multiple workers can be assigned", "state survives restart"),
        deadline=datetime(2026, 9, 1, tzinfo=timezone.utc),
    )

    assert project.state is ProjectState.PENDING
    assert project.constraints == ("open mandatory core", "phone-primary")
    assert project.acceptance_criteria[0] == "multiple workers can be assigned"
    assert project.created_at.tzinfo is not None
    assert project.last_progress_at == project.created_at


def test_task_models_dependencies_assignment_and_progress():
    task = Task(
        task_id="task-1",
        project_id="project-1",
        goal="Implement worker registry",
        acceptance_criteria=("two workers can register concurrently",),
        dependencies=("task-0",),
    )

    assert task.state is TaskState.PENDING
    assert task.dependencies == ("task-0",)
    assert task.assigned_workers == ()

    assigned = task.assign("agent-zero-1")
    assert assigned.state is TaskState.ASSIGNED
    assert assigned.assigned_workers == ("agent-zero-1",)
    assert assigned.last_progress_at >= task.last_progress_at


def test_task_assignment_is_graph_friendly_and_deduplicated():
    task = Task(task_id="task-1", project_id="project-1", goal="Cross-check result")
    task = task.assign("researcher").assign("verifier").assign("researcher")

    assert task.assigned_workers == ("researcher", "verifier")
