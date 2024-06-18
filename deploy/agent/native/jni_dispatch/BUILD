package(default_visibility = ["//tools/base/deploy/agent/native:all"])

cc_library(
    name = "jni_dispatch",
    srcs = [
        "jni_dispatch.cc",
    ],
    hdrs = [
        "jni_dispatch.h",
    ],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/deploy/common:common_lib",
        "//tools/base/deploy/common:log",
    ],
    alwayslink = True,
)

cc_binary(
    name = "jni_dispatch_dso",
    linkshared = True,
    deps = [
        ":jni_dispatch",
        "//prebuilts/studio/jdk:jni_headers",
    ],
)
