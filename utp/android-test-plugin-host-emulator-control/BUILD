load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_library(
    name = "android-test-plugin-host-emulator-control",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/com/android/tools/utp/plugins/host/emulatorcontrol/*java",
    ]),
    coordinates = "com.android.tools.utp:android-test-plugin-host-emulator-control:" + BASE_VERSION,
    resource_strip_prefix = "tools/base/utp/android-test-plugin-host-emulator-control/src/main/resources/",
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:tools.common",
        "//tools/base/emulator/proto",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:com.google.guava.guava",
        "@maven//:commons-io.commons-io",
        "@maven//:io.grpc.grpc-core",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:com.google.testing.platform.android-driver-instrumentation",
        "@maven//:com.google.crypto.tink.tink",
        "//tools/base/utp/android-test-plugin-host-emulator-control-proto",
        # Don't change dependency order or the test fails. Those plugins bundle kotlinx
        # coroutine jar which may be different version.
        "@maven//:com.google.testing.platform.android-device-provider-local",
        "@maven//:com.google.testing.platform.launcher",  # CompileOnly should not be in the pom
    ],
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/com/android/tools/utp/plugins/host/emulatorcontrol/*java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        # Don't change dependency order or the test fails. This launcher jar
        # should be loaded first.
        "@maven//:com.google.testing.platform.launcher",
        # Test dependencies.
        ":android-test-plugin-host-emulator-control",
        "@maven//:io.grpc.grpc-stub",
        "@maven//:io.grpc.grpc-testing",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.mockito.mockito-core",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/java/com/android/tools/utp/plugins/host/emulatorcontrol/*java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "build.gradle",
    ],
    visibility = ["//tools/base/utp:__pkg__"],
)
