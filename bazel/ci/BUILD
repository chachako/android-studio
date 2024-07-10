"""Continuous Integration scripts."""
load("@bazel_skylib//rules:common_settings.bzl", "string_flag", "bool_flag")

string_flag(
    name = "ab_target",
    build_setting_default = "",
    visibility = ["//tools/base/bazel/ci:__subpackages__"],
)

bool_flag(
    name = "is_ci",
    build_setting_default = False,
    visibility = ["//tools/base/bazel/ci:__subpackages__"],
)

py_library(
    name = "bazel",
    srcs = ["bazel.py"],
)

py_library(
    name = "errors",
    srcs = ["errors.py"],
)

py_library(
    name = "query_checks",
    srcs = ["query_checks.py"],
    deps = [
        ":bazel",
        ":errors",
    ],
)

py_library(
    name = "studio",
    srcs = ["studio.py"],
    deps = [":bazel"],
)

py_library(
    name = "studio_linux",
    srcs = ["studio_linux.py"],
    deps = [
        ":bazel",
        ":errors",
        ":studio",
    ],
)

py_library(
    name = "studio_win",
    srcs = ["studio_win.py"],
    deps = [
        ":bazel",
        ":errors",
        ":studio",
    ],
)

py_library(
    name = "ci_lib",
    srcs = ["ci.py"],
    deps = [
        ":bazel",
        ":errors",
        ":query_checks",
        ":studio_linux",
        ":studio_win",
    ],
)

py_binary(
    name = "ci",
    srcs = ["ci.py"],
    data = glob(["data/*"]),
    deps = [":ci_lib"],
)

py_test(
    name = "ci_test",
    srcs = ["ci_test.py"],
    deps = [
        ":ci_lib",
        "@absl-py//absl/testing:absltest",
    ],
)
