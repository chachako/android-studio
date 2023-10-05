load("//tools/base/bazel:gradle.bzl", "gradle_build")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/build-system:hybrid_build.bzl", "GRADLE_PROPERTIES")

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/*.java",
        "src/main/*.kt",
    ]) + [
        "build.gradle",
    ],
    visibility = ["//visibility:private"],
)

# keep sorted
PREVIEW_SCREENSHOT_GRADLE_PLUGIN_SOURCE_FILES = [
    ":agp_gradle_build_files",
    "//tools:agp_gradle_build_files",
    "//tools/base/build-system/builder-model:agp_gradle_build_files",
    "//tools/base/build-system/builder-test-api:agp_gradle_build_files",
    "//tools/base/build-system/gradle-api:agp_gradle_build_files",
    "//tools/base/common:agp_gradle_build_files",
    "//tools/buildSrc:agp_gradle_build_files",
    "//tools/buildSrc/base:agp_gradle_build_files",
]

gradle_build(
    name = "preview-screenshot-gradle-plugin",
    build_file = "//tools:agp_gradle_build_root_build_file",
    data = ["//prebuilts/studio/jdk:jdk11"] + PREVIEW_SCREENSHOT_GRADLE_PLUGIN_SOURCE_FILES,
    gradle_properties = GRADLE_PROPERTIES,
    output_file = "preview_screenshot-gradle-plugin.zip",
    output_file_source = "dist/repo.zip",
    repos = [
        "//tools/base:agp_artifacts",
        "//tools/base:agp_dependencies",
    ],
    tasks = [":zipPlugin"],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
        "//tools/base/gmaven:__pkg__",
    ],
)

kotlin_library(
    name = "lib",
    srcs = glob([
        "src/main/java/*.kt",
        "src/main/java/*.java",
    ]),
    deps = [],
)
