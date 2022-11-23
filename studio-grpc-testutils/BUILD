load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# Build studio-grpc-testutils as a standalone library without a dependency on "studio-sdk"
kotlin_library(
    name = "studio-grpc-testutils-base",
    srcs = glob([
        "src/**/*.kt",
    ]),
    deps = [
        "//tools/adt/idea/.idea/libraries:studio-grpc",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.studio-grpc-testutils",
    iml_files = ["studio-grpc-testutils.iml"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # testSrc is TEST scoped but contains no runnable tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:studio-grpc[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
    ],
)
