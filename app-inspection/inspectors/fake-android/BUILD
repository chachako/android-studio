load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

package(default_visibility = ["//visibility:public"])

kotlin_library(
    name = "fake_android",
    srcs = glob(["src/**/*.kt"]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "@maven//:org.xerial.sqlite-jdbc",
    ],
)
