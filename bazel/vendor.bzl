"""
Configure vendor specific repositories.

These are repositories that may only exist or work with internal sources,
and need to be substituted/supplied with vendor specific replacements.
"""

load("@//tools/base/bazel:repositories.bzl", "setup_vendor_repos")
load("@rules_android_ndk//:rules.bzl", "android_ndk_repository")

# buildifier: disable=unnamed-macro
def setup_vendor_repositories():
    setup_vendor_repos()

    android_ndk_repository(
        name = "androidndk",
        api_level = 27,
    )
    native.register_toolchains("@androidndk//:all")
