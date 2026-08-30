"""JARVIS Orchestrator standalone backend."""

from contextlib import asynccontextmanager
from importlib import import_module

# Keep the existing app module authoritative while installing management-plane
# routes without coupling its established command/session transport to workers.
_app_module = import_module(".app", __name__)
from .goal_api import install_goal_api
from .packaged_management import build_packaged_management_service
from .project_store import InMemoryProjectStore, ValkeyProjectStore
from .worker_registry import InMemoryWorkerRegistry, ValkeyWorkerRegistry

install_goal_api(_app_module)

_original_lifespan = _app_module.app.router.lifespan_context


@asynccontextmanager
async def _management_lifespan(app):
    async with _original_lifespan(app):
        valkey = getattr(app.state, "valkey", None)
        if valkey is None:
            store = InMemoryProjectStore()
            registry = InMemoryWorkerRegistry()
        else:
            store = ValkeyProjectStore(valkey)
            registry = ValkeyWorkerRegistry(valkey)

        app.state.goal_service = await build_packaged_management_service(
            store=store,
            registry=registry,
            runtime=app.state.runtime,
        )
        yield


_app_module.app.router.lifespan_context = _management_lifespan
