"""Compatibility shim for tests/imports that load backend.app as a package.

Render can import backend/ai_provider.py directly when running from the backend directory,
while repository-root test execution resolves this module and forwards the same API.
"""
from backend.ai_provider import *  # noqa: F401,F403
