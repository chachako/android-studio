load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")

TEST_ENGINE_VERSION = "0.0.1"

maven_library(
    name = "compose-preview-validation-junit-engine",
    srcs = glob([
        "src/main/java/com/android/tools/compose/*.kt",
    ]),
    coordinates = "com.android.tools.compose:compose-preview-validation-junit-engine",
    resource_strip_prefix = "tools/base/preview/screenshot/compose-preview-validation-junit-engine/src/main/resources/",
    resources = glob(["src/main/resources/**"]),
    version = select({
        "//tools/base/bazel:release": TEST_ENGINE_VERSION + "-eap03",
        "//conditions:default": TEST_ENGINE_VERSION + "-dev",
    }),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:tools.common",
        "//tools/base/multipreview-asm",
        "//tools/base/standalone-render/compose-cli-serialize:standalone-render.compose-cli-serialize",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.junit.platform.junit-platform-engine",
    ],
)

kotlin_test(
    name = "compose-preview-validation-junit-engine-tests",
    srcs = glob([
        "src/test/java/**/*.kt",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":compose-preview-validation-junit-engine",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)
