load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# Build target to verify that compose-preview-detector builds for tools/base
kotlin_library(
    name = "tools.compose-preview-detector",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:org.ow2.asm.asm",
    ],
)

# Build target to verify that compose-preview-detector tests succeed for tools/base
kotlin_test(
    name = "tools.compose-preview-detector.tests",
    srcs = glob([
        "testSrc/**/*.kt",
        "testSrc/**/*.java",
    ]),
    friends = ["//tools/base/multipreview-asm:tools.compose-preview-detector"],
    javacopts = ["-source 17"],
    jvm_flags = ["-Dtest.suite.jar=tools.compose-preview-detector.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.compose-preview-detector",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
        "@maven//:org.ow2.asm.asm",
    ],
)

# Performance tests for compose-preview-detector
kotlin_test(
    name = "perfgate-compose-preview-detector-tests",
    srcs = glob(["perf/testSrc/**/*.kt"]),
    tags = [
        "no_mac",
        "no_test_windows",
        "perfgate",
    ],
    test_class = "com.android.tools.preview.multipreview.PerfArtificialDataMultipreviewTest",
    deps = [
        ":tools.compose-preview-detector",
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
        "@maven//:org.ow2.asm.asm",
    ],
)

maven_library(
    name = "compose-preview-detector",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    coordinates = "com.android.tools.compose:compose-preview-detector",
    description = "Library to search for compose previews in the Java binary code.",
    module_name = "compose-preview-detector",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/multipreview-asm",
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.ow2.asm.asm",
    ],
)
