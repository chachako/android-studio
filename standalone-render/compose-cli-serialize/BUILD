load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# Build target to verify that compose-preview-renderer-model builds for tools/base
kotlin_library(
    name = "tools.compose-preview-renderer-model",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com.google.code.gson.gson",
    ],
)

# Build target to verify that compose-preview-renderer-model.tests tests succeeds for tools/base
kotlin_test(
    name = "tools.compose-preview-renderer-model.tests",
    srcs = glob([
        "testSrc/**/*.kt",
        "testSrc/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tools.compose-preview-renderer-model.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.compose-preview-renderer-model",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)

maven_library(
    name = "compose-preview-renderer-model",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    coordinates = "com.android.tools.compose:compose-preview-renderer-model",
    description = "Library to serialize and deserialize data to and from Compose rendering CLI",
    module_name = "compose-preview-renderer-model",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/standalone-render/compose-cli-serialize",
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com.google.code.gson.gson",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)
