load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_library(
    name = "common",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.utp:common",
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:tools.common",
        "@maven//:com.google.testing.platform.launcher",  # CompileOnly should not be in the pom
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        # Don't change dependency order or the test fails. This launcher jar
        # should be loaded first.
        "@maven//:com.google.testing.platform.launcher",
        # Test dependencies.
        ":common",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.mockito.mockito-core",
    ],
)