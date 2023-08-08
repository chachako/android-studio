load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# Build target to verify that multipreview builds for tools/base
kotlin_library(
    name = "tools.multipreview-asm",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:org.ow2.asm.asm",
    ],
)

# Build target to verify that multipreview tests succeed for tools/base
kotlin_test(
    name = "tools.multipreview-asm.tests",
    srcs = glob([
        "testSrc/**/*.kt",
        "testSrc/**/*.java",
    ]),
    friends = ["//tools/base/multipreview-asm:tools.multipreview-asm"],
    jvm_flags = ["-Dtest.suite.jar=tools.multipreview-asm.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.multipreview-asm",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
        "@maven//:org.ow2.asm.asm",
    ],
)
