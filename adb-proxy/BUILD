load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

kotlin_library(
    name = "reverse_lib",
    srcs = glob(["src/com/google/services/firebase/directaccess/client/device/remote/service/adb/forwardingdaemon/reverse/*.kt"]),
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.adb-proxy",
    srcs = ["src"],
    iml_files = ["adb-proxy.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/adt/idea/.idea/libraries:adb_proxy_proto",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

# Build adb-proxy client as a standalone library, with side effect of ensuring that
# it does not use unwanted dependencies from "studio-sdk" in the iml_module rule above
kotlin_library(
    name = "adb-proxy",
    srcs = glob([
        "src/**/*.kt",
    ]),
    visibility = [
        "//tools/vendor/google/directaccess-client:__subpackages__",
    ],
    deps = [
        "//tools/base/adblib",
    ],
)
