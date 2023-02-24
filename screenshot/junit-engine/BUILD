load("//tools/base/bazel:maven.bzl", "maven_library", "maven_repository")

TEST_ENGINE_VERSION = "0.0.1"

maven_library(
    name = "junit-engine",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.screenshot:junit-engine",
    resource_strip_prefix = "tools/base/screenshot/junit-engine/src/main/resources/",
    resources = glob(["src/main/resources/**"]),
    version = select({
        "//tools/base/bazel:release": TEST_ENGINE_VERSION + "-alpha01",
        "//conditions:default": TEST_ENGINE_VERSION + "-dev",
    }),
    visibility = ["//visibility:public"],
    deps = [
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
    ],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)
