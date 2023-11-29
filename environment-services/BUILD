load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.intellij.android.environment-services",
    srcs = ["src"],
    iml_files = ["intellij.android.environment-services.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    deps = ["//prebuilts/studio/intellij-sdk:studio-sdk"],
)

# Build target to verify that render-resources builds for tools/base
kotlin_library(
    name = "tools.environment-services",
    srcs = [
        "src/com/android/tools/environment/CancellationManager.kt",
        "src/com/android/tools/environment/Logger.kt",
        "src/com/android/tools/environment/log/NoopLogger.kt",
        "src/com/android/tools/environment/log/StubLoggerProvider.kt",
    ],
    resource_strip_prefix = "tools/base/environment-services/resources",
    resources = glob(["resources/**"]),
    visibility = ["//visibility:public"],
    deps = [],
)
