load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.screenshot.cli",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.screenshot.cli.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/base/lint/cli:studio.android.sdktools.lint.cli[module]",
    ],
)
