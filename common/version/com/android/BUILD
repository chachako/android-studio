load("//tools/base/bazel:expand_template.bzl", "expand_template")
load(
    "//tools/base/common:version.bzl",
    "DEV_BASE_VERSION",
    "DEV_BUILD_VERSION",
    "DEV_COMMANDLINE_TOOLS_VERSION",
    "RELEASE_BASE_VERSION",
    "RELEASE_BUILD_VERSION",
    "RELEASE_COMMANDLINE_TOOLS_VERSION",
)

expand_template(
    name = "versionProperties",
    out = "version.properties",
    substitutions = select({
        "//tools/base/bazel:release": {
            "{BUILD_VERSION}": RELEASE_BUILD_VERSION,
            "{BASE_VERSION}": RELEASE_BASE_VERSION,
            "{COMMANDLINE_TOOLS_VERSION}": RELEASE_COMMANDLINE_TOOLS_VERSION,
        },
        "//conditions:default": {
            "{BUILD_VERSION}": DEV_BUILD_VERSION,
            "{BASE_VERSION}": DEV_BASE_VERSION,
            "{COMMANDLINE_TOOLS_VERSION}": DEV_COMMANDLINE_TOOLS_VERSION,
        },
    }),
    template = "version.properties.template",
    visibility = [
        "//tools/base/build-system/builder-model:__pkg__",
        "//tools/base/common:__subpackages__",
        "//tools/data-binding:__pkg__",
    ],
)
