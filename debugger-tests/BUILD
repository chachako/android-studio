load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_SYSTEM_IMAGE")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

tags = [
    "no_windows",
    "no_mac",
]

kotlin_library(
    name = "debugger",
    srcs = glob(["src/**/*.kt"]),
    tags = tags,
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "@maven//:org.jetbrains.kotlinx.kotlinx-cli-jvm",
    ],
)

# A library with all tested classes
kotlin_library(
    name = "test-classes",
    srcs = glob(["resources/src/**/*.kt"]),
    tags = tags,
)

# A library with all tested classes so we can create a "*_deploy.jar" that contains all dependencies
java_binary(
    name = "test-classes-binary",
    main_class = "MainKt",
    tags = tags,
    runtime_deps = [
        ":test-classes",
    ],
)

# Test target
kotlin_test(
    name = "tests",
    srcs = glob(["testSrc/**/*.kt"]),
    data = glob(["resources/**"]) + [
        ":test-classes-binary_deploy.jar",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
    friends = [":debugger"],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        "-Dtest-classes-jar=$(location :test-classes-binary_deploy.jar)",
    ],
    tags = tags,
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":debugger",
        "//tools/adt/idea/as-driver:as-driver.utils_testlib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.jetbrains.kotlin.kotlin-test",
    ],
)

# A command line program that updates the golden files.
java_binary(
    name = "update-golden",
    data = glob(["resources/**"]) + [":test-classes-binary_deploy.jar"],
    jvm_flags = [
        "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
        "-Dtest-classes-jar=$(location :test-classes-binary_deploy.jar)",
    ],
    main_class = "com.android.tools.debuggertests.UpdateGoldenKt",
    tags = tags,
    runtime_deps = [
        ":debugger",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.debugger-tests",
    # do not sort: must match IML order
    srcs = [
        "resources/src",
        "src",
    ],
    iml_files = ["debugger-tests.iml"],
    resources = ["resources/res"],
    tags = tags,
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//prebuilts/tools/common/m2:kotlinx-cli-jvm-0.3.1",
    ],
)
