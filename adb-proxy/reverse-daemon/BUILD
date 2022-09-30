load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

package(default_visibility = ["//visibility:private"])

kotlin_library(
    name = "reverse_daemon_lib",
    srcs = glob(["src/**/*.kt"]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//tools/base/adb-proxy:reverse_lib",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
    ],
)

dex_library(
    name = "reverse_daemon_apk",
    dexer = "D8",
    jars = [
        ":reverse_daemon_lib",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        "//tools/base/adb-proxy:reverse_lib",
    ],
)

genrule(
    name = "daemon",
    srcs = [":reverse_daemon_apk.jar"],
    outs = ["reverse_daemon.dex"],
    # Extract the .dex file from the APK so we can execute it using app_process.
    cmd = "$(location //tools/base/bazel:unzipper) $(location :reverse_daemon_apk.jar) classes.dex:$(location :reverse_daemon.dex)",
    tools = ["//tools/base/bazel:unzipper"],
    visibility = ["//visibility:public"],
)
