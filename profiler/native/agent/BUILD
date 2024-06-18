load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "jni_library")

cc_binary(
    name = "libsupportjni.so",
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    linkshared = 1,
    visibility = ["//visibility:public"],
    deps = [
        ":support",
    ],
)

cc_library(
    name = "support",
    srcs = [
        "support/cpu_trace.cc",
        "support/event_passthrough.cc",
        "support/http_tracker.cc",
        "support/memory_stats_logger.cc",
        "support/memory_stats_logger.h",
        "support/profiler_service.cc",
        "support/user_counter_passthrough.cc",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/profiler/native/agent",
    ],
    linkstatic = 1,
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/profiler/native/perfd/common:trace_utils",
        "//tools/base/transport/native/agent",
        "//tools/base/transport/native/agent:jni_wrappers",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
    alwayslink = 1,
)

jni_library(
    name = "supportjni",
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
    deps = select({
        "@platforms//os:windows": [],
        "//conditions:default": [":libsupportjni.so.stripped"],
    }),
)

cc_library(
    name = "memory_component",
    srcs = ["memory_component.cc"],
    hdrs = ["memory_component.h"],
    copts = ANDROID_COPTS,
    visibility = ["//tools/base/transport/native/agent:__pkg__"],
    deps = [
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
)
