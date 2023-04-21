load("//tools/base/bazel:proto.bzl", "android_java_proto_library")
load("//tools/base/bazel:proto.bzl", "java_proto_library")

android_java_proto_library(
    name = "kotlin_multiplatform_android_models_proto",
    srcs = glob(["*.proto"]),
    visibility = ["//visibility:public"],
)

java_proto_library(
    name = "kotlin_multiplatform_android_models_proto_for_agp",
    srcs = glob(["*.proto"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob(["*.proto"]),
    visibility = ["//tools/base/build-system:__pkg__"],
)
