load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library", "maven_repository")
load("//tools/base/sdklib:sdk.bzl", "sdk_java_binary")
load("//tools/base/common:version.bzl", "BASE_VERSION")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load(":merge_deps.bzl", "merge_deps")

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
        "//tools/analytics-library/crash:analytics-crash[module]",
        "//tools/adt/idea/execution/common:intellij.android.execution.common[module]",
    ],
)

merge_deps(
    name = "bundled_deps",
    srcs = [
        ":lint-cli-psi-prebuilts",
        ":studio.android.sdktools.screenshot.cli",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/layoutlib/data:layoutlib",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/build-attribution:intellij.android.build-attribution",
        "//tools/adt/idea/compose-designer:intellij.android.compose-designer",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/server-flags:intellij.android.server-flags",
        "//tools/adt/idea/studio/build-flags",
        "//tools/analytics-library/crash:analytics-crash",
        "//tools/analytics-library/shared:analytics-shared",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/lint:studio.android.sdktools.lint-api",
        "//tools/base/lint:studio.android.sdktools.lint-checks",
        "//tools/base/lint:studio.android.sdktools.lint-model",
        "//tools/base/lint/cli:studio.android.sdktools.lint.cli",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
    ],
)

java_import(
    name = "bundled_deps_java_library",
    jars = ["bundled_deps.jar"],
)

maven_library(
    name = "screenshot",
    testonly = True,  # Mark as test-only so that it doesn't create coverage_baseline rule.
    srcs = [],
    bundled_deps = [":bundled_deps_java_library"],
    coordinates = "com.android.screenshot.cli:screenshot",
    description = "Screenshot tools",
    lint_baseline = "lint_baseline.xml",
    module_name = "screenshot",
    resource_strip_prefix = "tools/base/previewlib/cli",
    resources = [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ],
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
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

maven_repository(
    name = "screenshot_maven_repo",
    testonly = True,
    artifacts = [
        ":screenshot",
    ],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)
