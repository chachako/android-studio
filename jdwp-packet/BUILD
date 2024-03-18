load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.jdwppacket",
    srcs = ["src"],
    iml_files = ["android.sdktools.jdwppacket.iml"],
    jvm_target = "8",
    test_srcs = ["test/src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-java",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
    ],
)

kotlin_library(
    name = "jdwp-packet",
    srcs = glob([
        "src/**/*.kt",
    ]),
    visibility = [
        "//tools/base/jdwp-scache:__subpackages__",
        "//tools/base/jdwp-tracer:__subpackages__",
    ],
)

kotlin_test(
    name = "jdwp-packet.tests",
    srcs = glob([
        "test/src/**/*.kt",
    ]),
    # So that we can use jdwp-packet "internal" classes/functions from Unit Tests
    friends = [":jdwp-packet"],
    jvm_flags = ["-Dtest.suite.jar=jdwp-packet.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":jdwp-packet",
        # For "JarTestSuite"
        "//tools/base/testutils:tools.testutils",
        # For JUnit4 support
        "@maven//:junit.junit",
    ],
)
