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

py_test(
    name = "ci_test",
    srcs = ["ci_test.py"],
    deps = [
        ":ci_lib",
        "@absl-py//absl/testing:absltest",
    ],
)
