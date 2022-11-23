load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

java_binary(
    name = "agent_binary",
    srcs = glob([
        "src/**/*.java",
    ]),
    deploy_manifest_lines = [
        "Premain-Class: com.android.tools.instrumentation.threading.agent.Agent",
        "Boot-Class-Path: threading_agent.jar",
    ],
    main_class = "com.android.tools.instrumentation.threading.agent.Agent",
    deps = [
        "//tools/base/annotations",
        "//tools/base/threading-agent-callback:agent_callback",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-commons",
    ],
)

kotlin_library(
    name = "sample_kotlin_classes",
    srcs = glob(["testSrc/**/SampleClassesKotlin.kt"]),
    deps = [
        "//tools/base/annotations",
    ],
)

coverage_java_test(
    name = "agent_tests",
    srcs = glob(["testSrc/**/*.java"]),
    jvm_flags = ["-Dtest.suite.jar=agent_tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":agent_binary",
        ":sample_kotlin_classes",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/threading-agent-callback:agent_callback",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
        "@maven//:org.mockito.mockito-core",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-commons",
    ],
)

# Rule to build the `threading_agent.jar` that will be injected into the target JVM.
# It creates a single jar file with no external dependencies. It also repackages
# java libraries so that their versions loaded with the java agent by the boot classloader
# do not conflict with the versions of the same libraries loaded by the system class loader.
java_jarjar(
    name = "threading_agent",
    srcs = [
        ":agent_binary_deploy.jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)
