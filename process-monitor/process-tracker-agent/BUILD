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
    target_compatible_with = ["@platforms//os:android"],
)

android_cc_binary(
    name = "native",
    binary = ":process-tracker",
    filename = "process-tracker",
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
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
