"""
For now: Use the linkopts from skia.
"""

load("@skia_repo//include/config:linkopts.bzl", _DEFAULT_LINKOPTS = "DEFAULT_LINKOPTS")

DEFAULT_LINKOPTS = _DEFAULT_LINKOPTS
