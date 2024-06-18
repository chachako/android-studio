load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/base/bazel:utils.bzl", "merged_zip")

maven_repository(
    name = "maven_dependencies",
    # keep sorted
    artifacts = [
        "//tools/base/common:tools.common",
        "//tools/base/multipreview-asm:compose-preview-detector",
        "//tools/base/preview/screenshot/screenshot-validation-junit-engine",
        "//tools/base/standalone-render/compose-cli:compose-preview-renderer",
        "//tools/base/standalone-render/compose-cli-serialize:compose-preview-renderer-model",
    ],
)

# This includes transitive maven dependencies.
# Use :preview_screenshot_maven_repo for release.
merged_zip(
    name = "preview_screenshot_maven_repo_all",
    srcs = [
        ":maven_dependencies.zip",
        "//tools/base/preview/screenshot/screenshot-test-gradle-plugin:screenshot-test-gradle-plugin.zip",
    ],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

# Don't copy this build rule to other places (b/342217955).
# This build rule will be deleted after the screenshot test plugin
# is merged back to the Android Gradle plugin.
genrule(
    name = "preview_screenshot_maven_repo",
    srcs = [":preview_screenshot_maven_repo_all.zip"],
    outs = ["preview_screenshot_maven_repo.zip"],
    cmd_bash = """
      files_to_keep=(
        "com/android/compose/screenshot/com.android.compose.screenshot.gradle.plugin/"
        "com/android/compose/screenshot/screenshot-test-gradle-plugin/"
        "com/android/tools/compose/compose-preview-renderer/"
        "com/android/tools/compose/compose-preview-renderer-model/"
        "com/android/tools/screenshot/screenshot-validation-junit-engine/"
      )
      files_to_exclude=($$(zipinfo -1 "$<" | grep -v $${files_to_keep[@]/#/-e }) )

      tempdir=$$(mktemp -d -p "$(RULEDIR)")
      unzip -qq "$<" -d "$$tempdir"

      output_absl_path=$$(realpath "$@")
      cd "$$tempdir" && zip -qr -D "$$output_absl_path" * -x "$${files_to_exclude[@]}"

      rm -rf "$$tempdir"
    """,
    target_compatible_with = ["@platforms//os:linux"],
)
