load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.resource-repository",
    srcs = ["main/java"],
    iml_files = ["android.sdktools.resource-repository.iml"],
    test_data = [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/res",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/base/resource-repository/test/resources",
    ],
    test_srcs = ["test/java"],
    test_tags = [
        "block-network",
        "cpu:3",
        "no_test_windows",
    ],
    test_timeout = "long",  # "medium" is not sufficient on Mac b/155999536
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:aapt-proto",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/environment-services:studio.intellij.android.environment-services[module]",
    ],
)

java_binary(
    name = "framework-res-jar-creator",
    main_class = "com.android.resources.aar.FrameworkResJarCreator",
    runtime_deps = [":studio.android.sdktools.resource-repository"],
)

genrule(
    name = "framework_res",
    srcs = [
        "//prebuilts/studio/layoutlib/data/res",
        "//prebuilts/studio/layoutlib/data/res:version",
    ],
    outs = ["framework_res.jar"],
    cmd = "$(JAVA) -classpath $(location :framework-res-jar-creator_deploy.jar) " + "com.android.resources.aar.FrameworkResJarCreator " + "$(location //prebuilts/studio/layoutlib/data/res:version)/.. $(OUTS)",
    toolchains = ["//prebuilts/studio/jdk/jdk17:jdk17_runtime"],
    tools = [
        ":framework-res-jar-creator",
        ":framework-res-jar-creator_deploy.jar",
    ],
    visibility = ["//visibility:public"],
)

# Build target to verify that resource-repository builds for tools/base
kotlin_library(
    name = "tools.resource-repository",
    srcs = glob([
        "main/java/**/*.java",
        "main/java/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/build-system/aapt2-proto",
        "//tools/base/common:tools.common",
        "//tools/base/environment-services:tools.environment-services",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdk-common:tools.sdk-common",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:it.unimi.dsi.fastutil",
        "@maven//:net.sf.kxml.kxml2",
    ],
)

kotlin_library(
    name = "tools.resource-repository.test.util",
    srcs = [
        "test/java/com/android/resources/AarTestUtils.kt",
    ],
    visibility = [
        "//tools/adt/idea/render-resources:__pkg__",
    ],
    deps = [
        ":tools.resource-repository",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/testutils:tools.testutils",
    ],
)
