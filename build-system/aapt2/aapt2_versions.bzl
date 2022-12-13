load("//tools/base/common:version.bzl", "DEV_BUILD_VERSION", "RELEASE_BUILD_VERSION")
load("//prebuilts/tools/common/aapt:aapt2_version.bzl", AAPT2_BUILD = "AAPT2_VERSION")

# TODO(b/262365256) restrict this visibility
#visibility([
#    "//tools/base/build-system/aapt2",
#    "//tools/base/build-system/aapt2-proto",
#])

DEV_AAPT2_VERSION = DEV_BUILD_VERSION + "-" + AAPT2_BUILD
RELEASE_AAPT2_VERSION = RELEASE_BUILD_VERSION + "-" + AAPT2_BUILD
AAPT2_VERSION = select({
    "//tools/base/bazel:release": RELEASE_AAPT2_VERSION,
    "//conditions:default": DEV_AAPT2_VERSION,
})
