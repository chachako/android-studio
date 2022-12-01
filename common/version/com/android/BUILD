load("//tools/base/common:version.bzl", "BASE_VERSION", "BUILD_VERSION", "COMMANDLINE_TOOLS_VERSION")
load("//tools/base/bazel:expand_template.bzl", "expand_template")

expand_template(
    name = "versionProperties",
    out = "version.properties",
    substitutions = {
        "{BUILD_VERSION}": BUILD_VERSION,
        "{BASE_VERSION}": BASE_VERSION,
        "{COMMANDLINE_TOOLS_VERSION}": COMMANDLINE_TOOLS_VERSION,
    },
    template = "version.properties.template",
    visibility = [
        "//tools/base/build-system/builder-model:__pkg__",
        "//tools/base/common:__subpackages__",
        "//tools/data-binding:__pkg__",
    ],
)
