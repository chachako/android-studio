load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# Build target to verify that standalone-render.lib builds for tools/base
kotlin_library(
    name = "tools.standalone-render.lib",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    lint_baseline = "lint_baseline.xml",
    resource_strip_prefix = "tools/base/standalone-render/lib/resources",
    resources = glob(["resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//tools/adt/idea/layoutlib:tools.layoutlib",
        "//tools/adt/idea/render-resources:tools.render-resources",
        "//tools/adt/idea/rendering:tools.rendering",
        "//tools/analytics-library/crash:tools.analytics-crash",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
    ],
)

# Build target to verify that standalone-render.lib tests succeed for tools/base
kotlin_test(
    name = "tools.standalone-render.lib.tests",
    srcs = glob([
        "testSrc/**/*.kt",
        "testSrc/**/*.java",
    ]),
    # keep sorted
    data = [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/layoutlib/data/res",
        "//tools/base/standalone-render/lib/testData",
    ],
    jvm_flags = ["-Dtest.suite.jar=tools.standalone-render.lib.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.standalone-render.lib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)
