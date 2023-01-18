load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.jdwptracer",
    srcs = ["src"],
    iml_files = ["android.sdktools.jdwptracer.iml"],
    jvm_target = "8",
    test_srcs = ["test/src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
    ],
)

java_library(
    name = "jdwp-trace",
    srcs = glob([
        "src/**/*.java",
    ]),
    deps = [
        "//tools/base/annotations",
        "@maven//:com.google.code.gson.gson",
    ],
)

java_test(
    name = "jdwp-trace.tests",
    srcs = glob([
        "test/src/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=jdwp-trace.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":jdwp-trace",
        "//tools/base/annotations",
        # For "JarTestSuite"
        "//tools/base/testutils:tools.testutils",
        # For JUnit4 support
        "@maven//:junit.junit",
    ],
)
