"""Continuous Integration scripts."""

py_library(
    name = "bazel",
    srcs = ["bazel.py"],
)

py_library(
    name = "query_checks",
    srcs = ["query_checks.py"],
    imports = ["."],
    deps = [":bazel"],
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
        ":studio",
    ],
)

py_library(
    name = "studio_win",
    srcs = ["studio_win.py"],
    deps = [
        ":bazel",
        ":studio",
    ],
)

py_library(
    name = "ci_lib",
    srcs = ["ci.py"],
    deps = [
        ":bazel",
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
