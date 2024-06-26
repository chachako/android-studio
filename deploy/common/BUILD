load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "select_android")

cc_library(
    name = "common_lib",
    srcs = [
        "event.cc",
        "io.cc",
        "message_pipe_wrapper.cc",
        "socket.cc",
        "trace.cc",
        "utils.cc",
        "//tools/base/deploy/sites:sites.cc",
    ],
    hdrs = [
        "event.h",
        "io.h",
        "message_pipe_wrapper.h",
        "proto_pipe.h",
        "size_buffer.h",
        "socket.h",
        "trace.h",
        "utils.h",
        "//tools/base/deploy/sites:sites.h",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    visibility = ["//tools/base/deploy:__subpackages__"],
    deps = [
        ":log",
        "//tools/base/deploy/proto:cc_proto",
    ],
)

cc_library(
    name = "log",
    srcs = select_android(
        ["log.android.cc"],
        ["log.cc"],
    ),
    hdrs = [
        "log.h",
    ],
    copts = select_android(
        ["-std=c++11"],
        [],
    ) + select({
        "@platforms//os:windows": [],
        "//conditions:default": ["-std=c++11"],
    }),
    visibility = ["//tools/base/deploy:__subpackages__"],
    deps = [
        ":env",
    ],
)

cc_library(
    name = "env",
    srcs = select_android(
        ["env.android.cc"],
        ["env.cc"],
    ),
    hdrs = [
        "env.h",
    ],
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//tools/base/deploy:__subpackages__"],
)

cc_test(
    name = "common_test",
    size = "medium",
    srcs = [
        "message_pipe_wrapper_test.cc",
        "sites_test.cc",
        "socket_test.cc",
    ],
    linkstatic = 1,
    visibility = ["//tools/base/deploy:__subpackages__"],
    deps = [
        ":common_lib",
        "@googletest//:gtest_main",
    ],
)
