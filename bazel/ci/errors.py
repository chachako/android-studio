"""Defines an error class used by CI scripts."""

import dataclasses


@dataclasses.dataclass(frozen=True,kw_only=True)
class CIError(Exception):
  """Represents an error known to a CI script."""
  exit_code: int = 1
