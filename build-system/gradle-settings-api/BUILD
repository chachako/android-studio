load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/bazel:utils.bzl", "fileset")
load("//tools/base/common:version.bzl", "BUILD_VERSION")

fileset(
    name = "resources",
    srcs = glob([
        "src/main/resources/**",
    ]),
    mappings = {
        "src/main/resources/": "",
    },
)

maven_library(
    name = "gradle-settings-api",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    bundled_deps = [
        "//tools/base/build-system:gradle-api_neverlink",
    ],
    coordinates = "com.android.tools.build:gradle-settings-api",
    resources = [":resources"],
    version = BUILD_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
        "previous-gradle-apis/**",
    ]) + [
        "NOTICE",
        "build.gradle",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)

filegroup(
    name = "agp_gradle_metalava_test_files",
    srcs = glob([
        "src/metalavaTest/**/*.kt",
        "api/**/*.txt",
    ]),
    visibility = ["//tools/base/build-system:__pkg__"],
)
