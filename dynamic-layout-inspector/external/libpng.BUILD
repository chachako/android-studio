config_setting(
    name = "darwin",
    values = {"host_cpu": "darwin"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "windows",
    values = {"host_cpu": "x64_windows"},
    visibility = ["//visibility:public"],
)

cc_library(
    name = "libpng",
    srcs = glob(
        [
            "*.c",
            "*.h",
        ],
        exclude = ["png.h"],
    ) + select({
        "@platforms//cpu:arm64": glob(["arm/*.c"]),
        "@platforms//cpu:x86_64": glob(["intel/*.c"]),
    }),
    hdrs = ["png.h"],
    copts = select({
        "windows": [
            "-std=gnu89",
            "-Wall",
            "-Wno-error",
            "-Wno-unused-parameter",
            "-Wno-unused-but-set-variable",
        ],
        "//conditions:default": [
            "-std=gnu89",
            "-Wall",
            "-Werror",
            "-Wno-unused-parameter",
            "-Wno-unused-but-set-variable",
        ],
    }),
    includes = [
        ".",
    ],
    visibility = ["//visibility:public"],
    deps = ["@zlib_repo//:zlib"],
)
