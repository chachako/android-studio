cc_library(
    name = "testutils",
    srcs = glob([
        "**/*bazel.cc",
        "**/*.h",
    ]),
    hdrs = glob(["**/*.h"]),
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    include_prefix = "test",
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
)
