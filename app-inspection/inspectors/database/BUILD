load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/app-inspection:app_inspection.bzl", "app_inspection_jar")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

package(default_visibility = ["//visibility:public"])

android_java_proto_library(
    name = "database_inspector_java_proto",
    srcs = ["resources/proto/database-inspector.proto"],
)

agent_deps = [
    ":database_inspector_java_proto",
    "//prebuilts/studio/sdk:platforms/latest_jar",
    "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
    "//tools/base/app-inspection/inspectors/common:app-inspection.inspectors.common",
    "//tools/base/bazel:studio-proto",
    "@maven//:androidx.annotation.annotation",
]

kotlin_library(
    name = "agent-jar",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    resource_strip_prefix = "tools/base/app-inspection/inspectors/database/resources",
    resources = glob(["resources/META-INF/**"]),
    deps = agent_deps,
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
    deps = agent_deps,
)

kotlin_library(
    name = "agent_tests_lib",
    srcs = glob(["testSrc/**/*.kt"]),
    deps = [
        ":agent-jar",
        "//tools/base/app-inspection/inspectors/lib:androidx-test-monitor",
        "//tools/base/app-inspection/inspectors/lib:inspection-testing",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
        "@maven//:org.robolectric.robolectric",
    ],
)

android_local_test(
    name = "agent_tests",
    custom_package = "com.emptytest",
    jvm_flags = [
        "-Dtest.suite.jar=agent_tests_lib.jar",
    ],
    target_compatible_with = select({
        "@platforms//os:linux": [],
        "//conditions:default": ["@platforms//:incompatible"],
    }),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":agent_tests_lib",
        "@robolectric//bazel:android-all",
    ],
)

studio_data(
    name = "bundle",
    files = ["database-inspector.jar"],
    mappings = {"tools/base/app-inspection/inspectors/database/": "app-inspection/"},
)
