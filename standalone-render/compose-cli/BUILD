load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:maven.bzl", "maven_library")
load(":merge_deps.bzl", "merge_deps")

COMPOSE_CLI_VERSION = "0.0.1"

kotlin_library(
    name = "tools.standalone-render.compose-cli",
    srcs = glob([
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/adt/idea/preview-elements:tools.preview-elements",
        "//tools/base/multipreview-asm:tools.multipreview-asm",
        "//tools/base/standalone-render/compose-cli-serialize:tools.standalone-render.compose-cli-serialize",
        "//tools/base/standalone-render/lib:tools.standalone-render.lib",
    ],
)

merge_deps(
    name = "bundled_deps",
    srcs = [
        ":tools.standalone-render.compose-cli",
        "//prebuilts/studio/layoutlib/data:layoutlib",  # Should be runtime
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//tools/adt/idea/layoutlib:tools.layoutlib",
        "//tools/adt/idea/layoutlib-loader:tools.layoutlib-loader",
        "//tools/adt/idea/preview-elements:tools.preview-elements",
        "//tools/adt/idea/render-resources:tools.render-resources",
        "//tools/adt/idea/rendering:tools.rendering",
        "//tools/analytics-library/crash:tools.analytics-crash",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/apkparser/analyzer:tools.analyzer",
        "//tools/base/common:tools.common",
        "//tools/base/device_validator:tools.dvlib",
        "//tools/base/environment-services:tools.environment-services",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/multipreview-asm:tools.multipreview-asm",
        "//tools/base/repository:tools.repository",
        "//tools/base/resource-repository:tools.resource-repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/standalone-render/compose-cli-serialize:tools.standalone-render.compose-cli-serialize",
        "//tools/base/standalone-render/lib:tools.standalone-render.lib",
        "//tools/base/zipflinger",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
        "@maven//:jakarta.xml.bind.jakarta.xml.bind-api",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.glassfish.jaxb.jaxb-runtime",
        "@maven//:org.jetbrains.intellij.deps.trove4j",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-commons",
    ],
)

java_import(
    name = "bundled_deps_java_library",
    jars = ["bundled_deps.jar"],
)

maven_library(
    name = "standalone-render.compose-cli",
    srcs = [],
    bundled_deps = [":bundled_deps_java_library"],
    coordinates = "com.android.tools:standalone-render.compose-cli",
    description = "Screenshot render tools",
    module_name = "standalone-render.compose-cli",
    resource_strip_prefix = "tools/base/standalone-render/compose-cli",
    resources = [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ],
    version = select({
        "//tools/base/bazel:release": COMPOSE_CLI_VERSION + "-alpha01",
        "//conditions:default": COMPOSE_CLI_VERSION + "-dev",
    }),
    visibility = ["//visibility:public"],
)
