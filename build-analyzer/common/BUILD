load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.build-analyzer.common",
    srcs = ["src/main/kotlin"],
    iml_files = ["android.build-analyzer.common.iml"],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    test_srcs = ["src/test/kotlin"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
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
    deps = [
        "//tools/base/common:tools.common",
        "@maven//:com.google.code.gson.gson",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
    ],
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/kotlin/**/*.kt",
    ]),
    data = glob(["testData/**"]),
    friends = [":common"],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":common",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.jetbrains.kotlin.kotlin-test",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.kt",
    ]) + ["build.gradle"],
    visibility = ["//tools/base/build-system:__pkg__"],
)
