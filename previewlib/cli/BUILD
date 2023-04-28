load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/sdklib:sdk.bzl", "sdk_java_binary")
load("//tools/base/common:version.bzl", "BASE_VERSION")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "lint-cli-psi-prebuilts",
    # do not sort: must match IML order
    jars = [
        "//prebuilts/tools/common/lint-psi/intellij-core:lint-cli-psi-prebuilts_files",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler:lint-cli-psi-prebuilts_files",
        "//prebuilts/tools/common/lint-psi/uast:lint-cli-psi-prebuilts_files",
    ],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.screenshot.cli",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.screenshot.cli.iml"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/previewlib/cli:lint-cli-psi-prebuilts"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/previewlib/cli:lint-cli-psi-prebuilts",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/base/lint:studio.android.sdktools.lint-model[module]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//tools/base/lint/cli:studio.android.sdktools.lint.cli[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/compose-designer:intellij.android.compose-designer[module]",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle-java",
        "//tools/adt/idea/build-attribution:intellij.android.build-attribution[module]",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader[module]",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module]",
        "//tools/adt/idea/rendering:intellij.android.rendering[module]",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository[module]",
    ],
)

java_binary(
    name = "screenshot.runner",
    main_class = "com.android.screenshot.cli.Main",
    resources = [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/layoutlib/data/linux",
    ],
    runtime_deps = [
        ":studio.android.sdktools.screenshot.cli",
    ],
)
