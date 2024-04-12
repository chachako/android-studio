"""This module implements Robolectric rules."""

load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load(":coverage.bzl", "coverage_android_local_test")

def robolectric_test(
        name,
        srcs = [],
        deps = [],
        jvm_flags = [],
        friends = [],
        test_class = "com.android.testutils.JarTestSuite",
        custom_package = "org.robolectric.default",
        **kwargs):
    """A Robolectric test rule.

    Args:
        name: The sources of the library.
        srcs: The sources of the library.
        deps: The dependencies of this library.
        jvm_flags: Flags to pass to the jvm
        friends: a list of friend jars (allowing access to 'internal' members) out: the output jar file
        test_class: A test class to run
        custom_package: A custom package name
        **kwargs: arguments to pass through to android_local_test
    """
    lib_name = "%s_lib" % name

    kotlin_library(
        srcs = srcs,
        name = lib_name,
        lint_is_test_sources = True,
        coverage_baseline_enabled = False,
        friends = friends,
        testonly = True,
        deps = depset(
            deps +
            [
                "//prebuilts/tools/common/m2:androidx-monitor",
            ],
        ).to_list(),
        **kwargs
    )

    coverage_android_local_test(
        name = name + ".test",
        custom_package = custom_package,
        jvm_flags = jvm_flags + [
            "-Dtest.suite.jar=%s.jar" % lib_name,
        ],
        target_compatible_with = select({
            "@platforms//os:linux": [],
            "//conditions:default": ["@platforms//:incompatible"],
        }),
        test_class = test_class,
        deps = [
            ":%s" % lib_name,
            "//tools/base/testutils:tools.testutils",
            "@robolectric//bazel:android-all",
        ],
        **kwargs
    )

    native.test_suite(
        name = name,
        tests = [name + ".test"],
    )
