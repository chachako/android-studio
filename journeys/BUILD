load("//tools/base/bazel:gradle.bzl", "gradle_build")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/base/build-system:hybrid_build.bzl", "GRADLE_PROPERTIES")

# A file group that contains gradle build file and source files to build journeys gradle
# plugin using Gradle inside Bazel's sandbox.
filegroup(
    name = "gradle_project_files",
    # buildifier: disable=globs
    srcs = glob(["src/main/**/*.kt"]) + ["build.gradle"],
    visibility = ["//visibility:private"],
)

maven_repository(
    name = "maven_dependencies",
    # keep sorted
    artifacts = [
        "@maven//:com.google.protobuf.protobuf-gradle-plugin_0.9.3",
        "@maven//:com.googlecode.jarjar.jarjar_1.3",
        "@maven//:commons-lang.commons-lang_2.6",
        "@maven//:gradle.plugin.org.jetbrains.gradle.plugin.idea-ext.gradle-idea-ext_0.8.1",
        "@maven//:org.jetbrains.kotlin.kotlin-build-common_2.0.21",
        "@maven//:org.jetbrains.kotlin.kotlin-build-tools-impl_2.0.21",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_2.0.21",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.6.10",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_2.0.21",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_2.0.21",
        "@maven//:org.jsoup.jsoup_1.13.1",
        "@maven//:org.junit.junit-bom_5.9.3",
    ],
)

gradle_build(
    name = "journeys-gradle-plugin",
    build_file = "//tools:agp_gradle_build_root_build_file",
    data = [
        ":gradle_project_files",
        "//prebuilts/studio/jdk:jdk11",
        "//tools:agp_gradle_build_files",
        "//tools/base/common:agp_gradle_build_files",
        "//tools/buildSrc:agp_gradle_build_files",
        "//tools/buildSrc/base:agp_gradle_build_files",
    ],
    gradle_properties = GRADLE_PROPERTIES,
    output_file = "journeys-gradle-plugin.zip",
    output_file_source = "dist/repo.zip",
    repos = [
        ":maven_dependencies",
        "//tools/base/build-system/previous-versions:8.7.0",
    ],
    tasks = [":base:journeys:zipPlugin"],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)
