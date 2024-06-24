load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:utils.bzl", "fileset")

fileset(
    name = "resources",
    srcs = glob([
        "src/main/resources/**",
    ]),
    mappings = {
        "src/main/resources/": "",
    },
)

kotlin_library(
    name = "gradle-kotlin",
    srcs = glob([
        "src/main/java/**/*.kt",
    ]),
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/build-system:gradle-api_neverlink",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "NOTICE",
        "build.gradle",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
