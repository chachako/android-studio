load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/bazel:utils.bzl", "fileset")
load("//tools/base/common:version.bzl", "BUILD_VERSION")

fileset(
    name = "version.properties",
    srcs = ["//tools/base/common/version/com/android:version.properties"],
    mappings = {"//tools/base/common/version/com/android:": "com/android/builder/model/"},
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.builder-model",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.builder-model.iml"],
    javacopts = [
        "-source 8",
        "-target 8",
    ],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
    ],
)

maven_library(
    name = "builder-model",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.build:builder-model",
    javacopts = [
        "-source 8",
        "-target 8",
    ],
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/build-system/builder-model",
    resources = [":version.properties"],
    version = BUILD_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

kotlin_test(
    name = "tests",
    size = "small",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        "//tools/base/testutils:tools.testutils",
    ],
    deps = [
        ":builder-model",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "NOTICE",
        "build.gradle",
    ],
    visibility = [
        "//tools/base/build-system:__pkg__",
        "//tools/base/firebase/testlab/testlab-gradle-plugin:__pkg__",
        "//tools/base/preview/screenshot/preview-screenshot-gradle-plugin:__pkg__",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.builder-model.tests",
    iml_files = ["android.sdktools.builder-model.tests.iml"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module, test]",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)
