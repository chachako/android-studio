load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library", "maven_repository")

TEST_ENGINE_VERSION = "0.0.1"

maven_library(
    name = "junit-engine",
    srcs = glob([
        "src/main/java/com/android/tools/preview/screenshot/junit/engine/*.kt",
    ]),
    coordinates = "com.android.tools.preview.screenshot.junit.engine:junit-engine",
    resource_strip_prefix = "tools/base/preview/screenshot/junit-engine/src/main/resources/",
    resources = glob(["src/main/resources/**"]),
    version = select({
        "//tools/base/bazel:release": TEST_ENGINE_VERSION + "-alpha01",
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

maven_repository(
    name = "maven_repo",
    artifacts = [
        ":junit-engine",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.junit.platform.junit-platform-engine",
        "@maven//:net.sf.kxml.kxml2",
        "//tools/base/multipreview-asm",
        "//tools/base/common:tools.common",
        "//tools/base/standalone-render/compose-cli-serialize:standalone-render.compose-cli-serialize",
    ],
)

kotlin_test(
    name = "junit-engine-tests",
    srcs = glob([
        "src/test/java/**/*.kt",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":junit-engine",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)
