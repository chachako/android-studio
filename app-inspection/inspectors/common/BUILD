load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

package(default_visibility = ["//visibility:public"])

kotlin_library(
    name = "app-inspection.inspectors.common",
    srcs = glob([
        "src/com/android/tools/appinspection/common/*.kt",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
        "@maven//:androidx.annotation.annotation",
    ],
)

kotlin_library(
    name = "app-inspection.inspectors.common.testing",
    testonly = True,
    srcs = glob([
        "testSrc/com/android/tools/appinspection/common/testing/*.kt",
    ]),
    deps = [
        "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
    ],
)
