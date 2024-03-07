load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/app-inspection:app_inspection.bzl", "app_inspection_jar")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

package(default_visibility = ["//visibility:public"])

android_java_proto_library(
    name = "backgroundtask_inspector_java_proto",
    srcs = ["resources/proto/backgroundtask_inspection.proto"],
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)

agent_srcs = glob(["src/**/*.kt"])

agent_deps = [
    ":backgroundtask_inspector_java_proto",
    "//prebuilts/studio/sdk:platforms/latest_jar",
    "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
    "//tools/base/app-inspection/inspectors/common:app-inspection.inspectors.common",
    "//tools/base/bazel:studio-proto",
    "@maven//:androidx.annotation.annotation",
]

kotlin_library(
    name = "agent-jar",
    srcs = agent_srcs,
    resource_strip_prefix = "tools/base/app-inspection/inspectors/backgroundtask/resources",
    resources = glob(["resources/META-INF/**"]),
    deps = agent_deps,
)

app_inspection_jar(
    name = "agent",
    srcs = agent_srcs,
    out = "backgroundtask-inspection.jar",
    bundle_srcs = [
        "//tools/base/app-inspection/inspectors/common:app-inspection.inspectors.common",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
    ],
    d8_flags = [
        "--min-api 26",  # Background task inspector is only supported on O+ devices.
    ],
    inspection_resource_strip_prefix = "tools/base/app-inspection/inspectors/backgroundtask/resources",
    inspection_resources = glob(["resources/META-INF/**"]),
    proto = ":backgroundtask_inspector_java_proto",
    # do not sort: fake_android must come before latest_jar in the classpath to override small pieces of it
    deps = agent_deps,
)

kotlin_library(
    name = "agent_tests_lib",
    srcs = glob(["testSrc/**/*.kt"]),
    deps = [
        ":agent-jar",
        "//tools/base/app-inspection/inspectors/lib:androidx-test-monitor",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.robolectric.android-all-instrumented",
        "@maven//:org.robolectric.robolectric",
    ],
)

android_local_test(
    name = "agent_tests.test",
    custom_package = "org.robolectric.default",
    jvm_flags = [
        "-Dtest.suite.jar=agent_tests_lib.jar",
    ],
    tags = [
        "no_mac",
        "no_windows",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":agent_tests_lib",
        "@robolectric//bazel:android-all",
    ],
)

studio_data(
    name = "bundle",
    files = ["backgroundtask-inspection.jar"],
    mappings = {"tools/base/app-inspection/inspectors/backgroundtask/": "app-inspection/"},
)
