load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/preview/screenshot:version.bzl", "SCREENSHOT_TEST_PLUGIN_VERSION")

maven_library(
    name = "screenshot-validation-junit-engine",
    srcs = glob([
        "src/main/java/com/android/tools/screenshot/*.kt",
    ]),
    coordinates = "com.android.tools.screenshot:screenshot-validation-junit-engine",
    resource_strip_prefix = "tools/base/preview/screenshot/screenshot-validation-junit-engine/src/main/resources/",
    resources = glob(["src/main/resources/**"]),
    version = SCREENSHOT_TEST_PLUGIN_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/standalone-render/compose-cli-serialize:compose-preview-renderer-model",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.junit.platform.junit-platform-engine",
    ],
)

kotlin_test(
    name = "screenshot-validation-junit-engine-tests",
    srcs = glob([
        "src/test/java/**/*.kt",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":screenshot-validation-junit-engine",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)
