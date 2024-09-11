load("@//tools/base/bazel:proto.bzl", "cc_grpc_proto_library", "java_proto_library")

package(default_visibility = ["//visibility:public"])

java_proto_library(
    name = "perfetto_config_java_proto",
    srcs = ["protos/perfetto/config/perfetto_config.proto"],
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)

java_proto_library(
    name = "java_proto",
    srcs = ["protos/perfetto/trace/perfetto_trace.proto"],
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)

cc_grpc_proto_library(
    name = "cc_proto",
    srcs = ["protos/perfetto/config/perfetto_config.proto"],
    grpc_support = 1,
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
)
