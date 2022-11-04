load("@//tools/base/bazel:proto.bzl", "cc_grpc_proto_library", "java_proto_library")

package(default_visibility = ["//visibility:public"])

java_proto_library(
    name = "perfetto_config_java_proto",
    srcs = ["protos/perfetto/config/perfetto_config.proto"],
    grpc_support = True,
    java_deps = ["//external:grpc-all-java"],
)

java_proto_library(
    name = "java_proto",
    srcs = ["protos/perfetto/trace/perfetto_trace.proto"],
    grpc_support = True,
    java_deps = ["//external:grpc-all-java"],
)

cc_grpc_proto_library(
    name = "cc_proto",
    srcs = ["protos/perfetto/config/perfetto_config.proto"],
    grpc_support = 1,
    tags = ["no_windows"],
)
