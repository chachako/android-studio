load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BUILD_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.adblib.ddmlibcompatibility",
    srcs = ["src"],
    iml_files = ["android.sdktools.adblib.ddmlibcompatibility.iml"],
    test_srcs = ["test/src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
    ],
)

# Build adblib.ddmlibcompatibility as a standalone library, with side effect of ensuring that
# the library does not use unwanted dependencies from "studio-sdk" in the iml_module rule above
# Build with: bazel build //tools/base/adblib-ddmlibcompatibility:adblib.ddmlibcompatibility
maven_library(
    name = "adblib.ddmlibcompatibility",
    # buildifier: disable=bad-recursive-glob
    srcs = glob([
        "src/**/*.kt",
        "src/**/*.java",
    ]),
    coordinates = "com.android.tools.adblib:adblib-ddmlibcompatibility",
    description = "Compatibility library between ddmlib and adblib",
    friends = [
        "//tools/base/adblib:adblib",
        "//tools/base/adblib-tools:adblib.tools",
    ],
    notice = "LICENSE",
    resource_strip_prefix = "tools/base/adblib.ddmlibcompatibility",
    version = BUILD_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/adblib",
        "//tools/base/adblib-tools:adblib.tools",
        "//tools/base/ddmlib:tools.ddmlib",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-guava",
    ],
)

# Test adblib.ddmlibcompatibility as a standalone library, with side effect of ensuring that
# the library does not use unwanted dependencies from "studio-sdk" in the iml_module rule above
# Run tests with:
# bazel test //tools/base/adblib-ddmlibcompatibility:adblib.ddmlibcompatibility.tests.test
kotlin_test(
    name = "adblib.ddmlibcompatibility.tests",
    # buildifier: disable=bad-recursive-glob
    srcs = glob([
        "test/src/**/*.kt",
        "test/src/**/*.java",
    ]),
    friends = [
        "//tools/base/adblib:adblib",
        ":adblib.ddmlibcompatibility",
        "//tools/base/adblib:adblib.tests.testlib",
    ],
    jvm_flags = ["-Dtest.suite.jar=adblib.ddmlibcompatibility.tests.jar"],
    lint_baseline = "lint_baseline.xml",
    test_class = "com.android.testutils.JarTestSuite",
    visibility = ["//visibility:public"],
    deps = [
        ":adblib.ddmlibcompatibility",
        "//tools/base/adblib:adblib.tests.testlib",
        "//tools/base/adblib-tools:adblib.tools.tests.testlib",
        # For "JarTestSuite"
        "//tools/base/testutils:tools.testutils",
        # For JUnit4 support
        "@maven//:junit.junit",
        # FakeAdbServer
        "//tools/base/fakeadbserver:tools.fakeadbserver",
        # FakeAdbRule
        "//tools/base/ddmlib:test-utils",
    ],
)
