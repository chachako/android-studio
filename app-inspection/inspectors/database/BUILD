load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/app-inspection:app_inspection.bzl", "app_inspection_jar")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
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

kotlin_test(
    name = "agent_tests",
    srcs = glob(["testSrc/**/*.kt"]),
    data = [
        "@maven//:org.robolectric.android-all-instrumented_9-robolectric-4913185-2-i4",
    ],
    jvm_flags = [
        "-Dtest.suite.jar=agent_tests.jar",
        # The following properties configure Robolectric to use a local Maven repo. The `user.home`
        # is required because Robolectric creates a lock file there but when running from the
        # sandbox, there is no home dir set.
        "-Duser.home=.",
        "-Drobolectric.dependency.repo.id=local",
        "-Drobolectric.dependency.repo.url=file:external/maven/repository",
    ],
    tags = [
        "no_mac",
        "no_windows",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        "//prebuilts/studio/sdk:platforms/latest_runtime_jar",
    ],
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

studio_data(
    name = "bundle",
    files = ["database-inspector.jar"],
    mappings = {"tools/base/app-inspection/inspectors/database/": "app-inspection/"},
)
