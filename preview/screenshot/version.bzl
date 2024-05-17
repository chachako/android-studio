"""
This module contains variables storing the next release version number of the screenshot test plugin.
"""

load("//tools/base/preview/screenshot:release_version.bzl", "SCREENSHOT_TEST_PLUGIN_VERSION_DEV", "SCREENSHOT_TEST_PLUGIN_VERSION_RELEASE")

SCREENSHOT_TEST_PLUGIN_VERSION = select({
    "//tools/base/bazel:release": SCREENSHOT_TEST_PLUGIN_VERSION_RELEASE,
    "//conditions:default": SCREENSHOT_TEST_PLUGIN_VERSION_DEV,
})

SCREENSHOT_TEST_PLUGIN_GRADLE_PROPERTIES = select({
    "//tools/base/bazel:release": {
        "SCREENSHOT_TEST_PLUGIN_VERSION": SCREENSHOT_TEST_PLUGIN_VERSION_RELEASE,
    },
    "//conditions:default": {
        "SCREENSHOT_TEST_PLUGIN_VERSION": SCREENSHOT_TEST_PLUGIN_VERSION_DEV,
    },
})
