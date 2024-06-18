package(default_visibility = ["//visibility:public"])

cc_library(
    name = "slicer",
    srcs = [
        "bytecode_encoder.cc",
        "code_ir.cc",
        "common.cc",
        "control_flow_graph.cc",
        "debuginfo_encoder.cc",
        "dex_bytecode.cc",
        "dex_format.cc",
        "dex_ir.cc",
        "dex_ir_builder.cc",
        "dex_utf8.cc",
        "instrumentation.cc",
        "reader.cc",
        "tryblocks_encoder.cc",
        "writer.cc",
    ],
    hdrs = glob([
        "export/slicer/*.h",
    ]),
    copts = select({
        "@platforms//os:windows": [],
        "//conditions:default": ["-std=c++11"],
    }),
    includes = ["export"],
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
    deps = [
        "@zlib_repo//:zlib",
    ],
)
