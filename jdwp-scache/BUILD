load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.jdwpscache",
    srcs = ["src"],
    iml_files = ["android.sdktools.jdwpscache.iml"],
    jvm_target = "8",
    test_srcs = ["test/src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/jdwp-packet:studio.android.sdktools.jdwppacket[module]",
    ],
)

kotlin_library(
    name = "jdwp-scache",
    srcs = glob([
        "src/**/*.kt",
    ]),
    module_name = "scache",
    visibility = [
        "//tools/base:__subpackages__",
        "//tools/base/jdwp-packet:__subpackages__",
        "//tools/base/jdwp-tracer:__subpackages__",
    ],
    deps = [
        "//tools/base/jdwp-packet",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

kotlin_test(
    name = "jdwp-scache.tests",
    srcs = glob([
        "test/src/**/*.kt",
    ]),
    # So that we can use jdwp-packet "internal" classes/functions from Unit Tests
    friends = [":jdwp-scache"],
    jvm_flags = ["-Dtest.suite.jar=jdwp-scache.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":jdwp-scache",
        # For "JarTestSuite"
        "//tools/base/testutils:tools.testutils",
        # For JUnit4 support
        "@maven//:junit.junit",
    ],
)
