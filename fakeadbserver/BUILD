load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/common:version.bzl", "BASE_VERSION")
load("//tools/base/bazel:proto.bzl", "java_proto_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.fakeadbserver",
    iml_files = ["android.sdktools.fakeadbserver.iml"],
    test_srcs = [
        "src/main/java",
        "src/test/java",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:libadb-server-proto",
    ],
)

maven_library(
    name = "tools.fakeadbserver",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    bundled_deps = [
        "//tools/base/adblib:adb-server-proto",
    ],
    coordinates = "com.android.tools.fakeadbserver:fakeadbserver",
    description = "Library implementing the ADB Server protocol",
    module_name = "fakeadbserver",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/fakeadbserver",
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:tools.common",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

kotlin_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    lint_baseline = "lint_baseline.xml",
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.fakeadbserver",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
    ],
)
