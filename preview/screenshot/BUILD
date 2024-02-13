load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/base/bazel:utils.bzl", "merged_zip")

maven_repository(
    name = "maven_dependencies",
    # keep sorted
    artifacts = [
        "//tools/base/common:tools.common",
        "//tools/base/multipreview-asm",
        "//tools/base/preview/screenshot/junit-engine",
        "//tools/base/standalone-render/compose-cli:standalone-render.compose-cli",
        "//tools/base/standalone-render/compose-cli-serialize:standalone-render.compose-cli-serialize",
    ],
)

merged_zip(
    name = "preview_screenshot_maven_repo",
    srcs = [
        ":maven_dependencies.zip",
        "//tools/base/preview/screenshot/preview-screenshot-gradle-plugin:preview_screenshot-gradle-plugin.zip",
    ],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)
