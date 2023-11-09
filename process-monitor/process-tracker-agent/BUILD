load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_binary(
    name = "process-tracker",
    srcs = glob([
        "**/*.cc",
        "**/*.h",
    ]),
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    stamp = 0,
    tags = [
        "manual",
        "no_windows",
        # This target relies on Android transitions, and only builds as a dependency of android_cc_binary rules.
        # See b/181808614#comment6.
        "no_linux",
    ],
)

android_cc_binary(
    name = "native",
    abis = [
        "x86",
        "x86_64",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = ":process-tracker",
    filename = "process-tracker",
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)

studio_data(
    name = "bundle",
    files = select({
        "@platforms//os:linux": [
            "native/arm64-v8a/process-tracker",
            "native/armeabi-v7a/process-tracker",
            "native/x86/process-tracker",
            "native/x86_64/process-tracker",
        ],
        "@platforms//os:macos": [
            "native/arm64-v8a/process-tracker",
            "native/armeabi-v7a/process-tracker",
            "native/x86/process-tracker",
            "native/x86_64/process-tracker",
        ],
        "@platforms//os:windows": [],
    }),
    mappings = {
        "tools/base/process-monitor/process-tracker-agent/": "process-tracker-agent/",
    },
    visibility = ["//visibility:public"],
)
