load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.intellij.android.environment-services",
    srcs = ["src"],
    iml_files = ["intellij.android.environment-services.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-java",
    ],
)

# Build target to verify that render-resources builds for tools/base
kotlin_library(
    name = "tools.environment-services",
    srcs = glob(["src/**/*.kt"]),
    resource_strip_prefix = "tools/base/environment-services/resources",
    resources = glob(["resources/**"]),
    visibility = ["//visibility:public"],
    deps = [],
)
