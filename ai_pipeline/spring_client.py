"""Compatibility import; MOTIF v0.3 callbacks are implemented in motif_client."""
from .motif_client import CallbackError, MotifClient
__all__ = ["CallbackError", "MotifClient"]
