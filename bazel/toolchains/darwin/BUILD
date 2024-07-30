load("@bazel_tools//tools/python:toolchain.bzl", "py_runtime_pair")

py_runtime(
    name = "prebuilt_python3",
    files = ["@//prebuilts/python/darwin-x86"],
    interpreter = "@//prebuilts/python/darwin-x86:bin/python3",
    python_version = "PY3",
)

py_runtime_pair(
    name = "prebuilt_python",
    py2_runtime = None,
    py3_runtime = ":prebuilt_python3",
)

toolchain(
    name = "python_toolchain",
    exec_compatible_with = [
        "@platforms//os:macos",
    ],
    toolchain = ":prebuilt_python",
    toolchain_type = "@bazel_tools//tools/python:toolchain_type",
)
