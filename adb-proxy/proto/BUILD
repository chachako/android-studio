package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")

android_java_proto_library(
    name = "adb_proxy_studio_proto",
    srcs = glob(["*.proto"]),
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)

java_proto_library(
    name = "adb_proxy_java_proto",
    srcs = glob(["*.proto"]),
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)
