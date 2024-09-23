load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:merge_archives.bzl", "merge_jars")
load("//tools/base/bazel:utils.bzl", "replace_manifest")
load(":aar_lint.bzl", "aar_lint")

ANDROIDX__VERSION = "1.7.0-beta03"

# AndroidX lint check composed from
# https://cs.android.com/search?q=lintPublish%20file:compose%2F%20file:build.gradle&ss=androidx%2Fplatform%2Fframeworks%2Fsupport&start=1

aar_lint(
    name = "compose_runtime_lint",
    dep = "@maven//:androidx.compose.runtime.runtime-android_" + ANDROIDX__VERSION,
)

aar_lint(
    name = "compose_ui_lint",
    dep = "@maven//:androidx.compose.ui.ui-android_" + ANDROIDX__VERSION,
)

aar_lint(
    name = "compose_ui_graphics_lint",
    dep = "@maven//:androidx.compose.ui.ui-graphics-android_" + ANDROIDX__VERSION,
)

aar_lint(
    name = "compose_ui_test_manifest_lint",
    dep = "@maven//:androidx.compose.ui.ui-test-manifest_" + ANDROIDX__VERSION,
)

aar_lint(
    name = "compose_runtime_saveable_lint",
    dep = "@maven//:androidx.compose.runtime.runtime-saveable-android_" + ANDROIDX__VERSION,
)

aar_lint(
    name = "compose_material_lint",
    #TODO import newer version to match the rest
    dep = "@maven//:androidx.compose.material.material-android_" + "1.6.6",
)

aar_lint(
    name = "compose_animation_lint",
    dep = "@maven//:androidx.compose.animation.animation-android_" + ANDROIDX__VERSION,
)

aar_lint(
    name = "compose_animation_core_lint",
    dep = "@maven//:androidx.compose.animation.animation-core-android_" + ANDROIDX__VERSION,
)

aar_lint(
    name = "compose_foundation_lint",
    dep = "@maven//:androidx.compose.foundation.foundation-android_" + ANDROIDX__VERSION,
)

aar_lint(
    name = "compose_material3_lint",
    dep = "@maven//:androidx.compose.material3.material3-android_1.2.1",
)

kotlin_library(
    name = "compose-desktop-registry",
    srcs = ["ComposeDesktopIssueRegistry.kt"],
    lint_enabled = False,
    deps = [
        ":compose_animation_core_lint",
        ":compose_animation_lint",
        ":compose_foundation_lint",
        ":compose_material3_lint",
        ":compose_material_lint",
        ":compose_runtime_lint",
        ":compose_runtime_saveable_lint",
        ":compose_ui_graphics_lint",
        ":compose_ui_lint",
        ":compose_ui_test_manifest_lint",
        "//tools/base/lint:tools.lint-api",
    ],
)

merge_jars(
    name = "androidx-checks-merged",
    out = "androidx-checks-merged.jar",
    allow_duplicates = True,
    jars = [
        ":compose_animation_core_lint",
        ":compose_animation_lint",
        ":compose_foundation_lint",
        ":compose_material_lint",
        ":compose_material3_lint",
        ":compose_runtime_lint",
        ":compose_runtime_saveable_lint",
        ":compose_ui_graphics_lint",
        ":compose_ui_lint",
        ":compose_ui_test_manifest_lint",
        ":compose-desktop-registry",
    ],
)

replace_manifest(
    name = "compose-desktop-checks",
    manifest = "MANIFEST.MF",
    original_jar = ":androidx-checks-merged",
    visibility = ["//visibility:public"],
)
