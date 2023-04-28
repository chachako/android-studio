load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library", "maven_proto_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_proto_library(
    name = "proto",
    srcs = glob(["*.proto"]),
    coordinates = "com.android.tools.emulator:proto",
    description = "A protobuf messages to control Android Emulator",
    grpc_support = True,
    java_deps = [
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
        "@maven//:io.grpc.grpc-core",
        "@maven//:io.grpc.grpc-netty",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:io.grpc.grpc-stub",
    ],
    pom_name = "Emulator protos for Android Tools",
    resource_strip_prefix = "tools/base/emulator/proto",
    resources = glob(["*.proto"]),
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
)

android_java_proto_library(
    name = "emulator_java_proto",
    srcs = [
        "emulator_controller.proto",
        "ice_config.proto",
        "rtc_service_v2.proto",
        "snapshot.proto",
        "snapshot_service.proto",
        "ui_controller_service.proto",
    ],
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
    visibility = ["//visibility:public"],
)
