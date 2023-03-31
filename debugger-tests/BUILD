load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.debugger-tests",
    iml_files = ["debugger-tests.iml"],
    test_resources = ["testResources/res"],
    test_srcs = [
        "testSrc",
        "testResources/src",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

# Build jar file from test resources
kotlin_library(
    name = "tested-code",
    srcs = glob(["testResources/src/**/*.kt"]),
    visibility = ["//visibility:public"],
    deps = [
    ],
)
