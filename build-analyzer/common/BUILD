load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.build-analyzer.common",
    iml_files = ["android.build-analyzer.common.iml"],
    jvm_target = "8",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

kotlin_library(
    name = "common",
    srcs = glob([
        "src/main/kotlin/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.kt",
    ]) + ["build.gradle"],
    visibility = ["//tools/base/build-system:__pkg__"],
)
