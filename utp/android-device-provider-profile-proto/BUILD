load("//tools/base/bazel:proto.bzl", "INTELLIJ_PLATFORM_PROTO_VERSION", "maven_proto_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_proto_library(
    name = "android-device-provider-profile-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    coordinates = "com.android.tools.utp:android-device-provider-profile-proto",
    description = "A protobuf messages used by device providers to give detailed profiling messages",
    notice = "NOTICE",
    protoc_version = INTELLIJ_PLATFORM_PROTO_VERSION,
    resource_strip_prefix = "tools/base/utp/android-device-provider-profile-proto/src/main/proto",
    resources = glob(["src/main/proto/*.proto"]),
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
)
