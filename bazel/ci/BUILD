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
    name = "ci_lib",
    srcs = ["ci.py"],
    imports = ["."],
    deps = [
        ":bazel",
        ":query_checks",
    ],
)

py_binary(
    name = "ci",
    srcs = ["ci.py"],
    data = glob(["data/*"]),
    imports = ["."],
    deps = [
        ":bazel",
        ":query_checks",
    ],
)

# TODO(b/307355082) add external/python/absl-py/ project and write tests.

