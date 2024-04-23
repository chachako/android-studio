load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/app-inspection:app_inspection.bzl", "app_inspection_jar")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")
load("//tools/base/bazel:robolectric.bzl", "robolectric_test")

package(default_visibility = ["//visibility:public"])

android_java_proto_library(
    name = "network_inspector_java_proto",
    srcs = ["resources/proto/network-inspector.proto"],
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)

agent_srcs = glob(["src/**/*.kt"])

agent_deps = [
    ":network_inspector_java_proto",
    "//prebuilts/studio/sdk:platforms/latest_jar",
    "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
    "//prebuilts/tools/common/m2:io.grpc.grpc-api.1.57.0",
    "//tools/base/app-inspection/inspectors/common:app-inspection.inspectors.common",
    "//tools/base/bazel:studio-proto",
    "@maven//:androidx.annotation.annotation",
    "@maven//:com.squareup.okhttp.okhttp",
    "@maven//:com.squareup.okhttp3.okhttp",
    "@maven//:com.squareup.okio.okio",
    "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
]

kotlin_library(
    name = "agent-jar",
    srcs = agent_srcs,
    resource_strip_prefix = "tools/base/app-inspection/inspectors/network/resources",
    resources = glob(["resources/META-INF/**"]),
    deps = agent_deps,
)

app_inspection_jar(
    name = "agent",
    srcs = agent_srcs,
    out = "network-inspector.jar",
    bundle_srcs = [
        "//tools/base/app-inspection/inspectors/common:app-inspection.inspectors.common",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core-jvm",
    ],
    d8_flags = [
        "--min-api 26",  # Network inspector is only supported on O+ devices.
    ],
    inspection_resource_strip_prefix = "tools/base/app-inspection/inspectors/network/resources",
    inspection_resources = glob(["resources/META-INF/**"]),
    proto = ":network_inspector_java_proto",
    deps = agent_deps,
)

java_proto_library(
    name = "test_server_java_proto",
    srcs = ["testProto/test-server.proto"],
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)

robolectric_test(
    name = "agent_tests",
    srcs = glob(["testSrc/**/*.kt"]),
    custom_package = "org.robolectric.default",
    friends = [":agent-jar"],
    deps = [
        ":agent-jar",
        ":test_server_java_proto",
        "//tools/base/app-inspection/inspectors/common:app-inspection.inspectors.common.testing",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.jetbrains.kotlin.kotlin-test",
        "@maven//:org.mockito.mockito-core",
        "@maven//:org.robolectric.robolectric",
    ],
)

studio_data(
    name = "bundle",
    files = ["network-inspector.jar"],
    mappings = {"tools/base/app-inspection/inspectors/network/": "app-inspection/"},
)
