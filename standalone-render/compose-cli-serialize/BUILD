load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# Build target to verify that standalone-render.compose-cli-serialize builds for tools/base
kotlin_library(
    name = "tools.standalone-render.compose-cli-serialize",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com.google.code.gson.gson",
    ],
)

# Build target to verify that standalone-render.compose-cli-serialize.tests tests succeeds for tools/base
kotlin_test(
    name = "tools.standalone-render.compose-cli-serialize.tests",
    srcs = glob([
        "testSrc/**/*.kt",
        "testSrc/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tools.standalone-render.compose-cli-serialize.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.standalone-render.compose-cli-serialize",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)
