load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_library(
    name = "declarative-gradle-model",
    srcs = glob([
        "src/main/kotlin/**/*.kt",
    ]),
    coordinates = "com.android.tools.gradle.declarative:model",
    resource_strip_prefix = "tools/base/declarative/model/src/main/resources/",
    resources = glob(["src/main/resources/**"]),
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:tools.common",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
        "@maven//:org.tomlj.tomlj",
    ],
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/kotlin/**/*.kt",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":declarative-gradle-model",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.mockito.mockito-core",
        "@maven//:org.tomlj.tomlj",
    ],
)

load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.declarative-gradle.model",
    srcs = ["src/main/kotlin"],
    iml_files = ["declarative-gradle.model.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/.idea/libraries:tomlj",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.declarative-gradle.model-tests",
    iml_files = ["declarative-gradle.model-tests.iml"],
    test_srcs = ["src/test/kotlin"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/declarative-gradle/model:studio.declarative-gradle.model[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/.idea/libraries:tomlj[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-junit[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
    ],
)
