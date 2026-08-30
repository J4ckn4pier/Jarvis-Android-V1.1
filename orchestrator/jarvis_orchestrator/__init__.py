"""JARVIS Orchestrator standalone backend."""

from importlib import import_module

# Keep the existing app module authoritative while installing management-plane
# routes without coupling its established command/session transport to workers.
_app_module = import_module(".app", __name__)
from .goal_api import install_goal_api

install_goal_api(_app_module)
