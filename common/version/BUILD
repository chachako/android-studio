load("//tools/base/common:version.bzl", "BASE_VERSION", "BUILD_VERSION")

java_library(
    name = "version",
    srcs = ["Version.java"],
    resource_strip_prefix = "tools/base/common/version",
    resources = ["//tools/base/common/version/com/android:version.properties"],
    visibility = ["//tools/base/common:__pkg__"],
)

# This library does not include resources because the maven_library includes
# com/android/version.properties from a different resource.
java_library(
    name = "without_resources",
    srcs = ["Version.java"],
    visibility = ["//tools/base/common:__pkg__"],
)

java_test(
    name = "version_test",
    srcs = ["VersionTest.java"],
    data = ["//tools/buildSrc/base:version.properties"],
    jvm_flags = [
        "-Dtest.version.properties=$(location //tools/buildSrc/base:version.properties)",
        "-Dtest.version.baseVersion=" + BASE_VERSION,
        "-Dtest.version.buildVersion=" + BUILD_VERSION,
    ],
    test_class = "com.android.VersionTest",
    deps = [
        ":version",
        "@maven//:junit.junit",
    ],
)
