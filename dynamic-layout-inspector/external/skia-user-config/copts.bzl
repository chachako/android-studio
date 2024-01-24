"""
For now: Use the copts from skia.
"""

load("@skia_repo//include/config:copts.bzl", _DEFAULT_COPTS = "DEFAULT_COPTS", _DEFAULT_OBJC_COPTS = "DEFAULT_OBJC_COPTS")

DEFAULT_COPTS = _DEFAULT_COPTS + ["-Wno-ignored-attributes"]
DEFAULT_OBJC_COPTS = _DEFAULT_OBJC_COPTS
