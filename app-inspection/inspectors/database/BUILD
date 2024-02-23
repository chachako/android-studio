load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/app-inspection:app_inspection.bzl", "app_inspection_jar")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

package(default_visibility = ["//visibility:public"])

android_java_proto_library(
    name = "database_inspector_java_proto",
    srcs = ["resources/proto/database-inspector.proto"],
)

app_inspection_jar(
    name = "agent",
    srcs = glob([
        "src/**/*.kt",
        "src/**/*.java",
    ]),
    out = "database-inspector.jar",
    bundle_srcs = [
        "//tools/base/app-inspection/inspectors/common:app-inspection.inspectors.common",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
    ],
    d8_flags = [
        "--min-api 26",  # Database inspector is only supported on O+ devices.
    ],
    inspection_resource_strip_prefix = "tools/base/app-inspection/inspectors/database/resources",
    inspection_resources = glob(["resources/META-INF/**"]),
    proto = ":database_inspector_java_proto",
    deps = [
        ":database_inspector_java_proto",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
        "//tools/base/app-inspection/inspectors/common:app-inspection.inspectors.common",
        "//tools/base/bazel:studio-proto",
        "@maven//:androidx.annotation.annotation",
    ],
)

studio_data(
    name = "bundle",
    files = ["database-inspector.jar"],
    mappings = {"tools/base/app-inspection/inspectors/database/": "app-inspection/"},
)
