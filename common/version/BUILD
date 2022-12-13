load(
    "//tools/base/common:version.bzl",
    "DEV_BASE_VERSION",
    "DEV_BUILD_VERSION",
    "RELEASE_BASE_VERSION",
    "RELEASE_BUILD_VERSION",
)

java_library(
    name = "version",
    srcs = ["Version.java"],
    # This compiled class is used in both the studio
    # (//tools/base/common:studio.android.sdktools.common) and tools
    # (//tools/base/common:tools.common) builds, and so is constrained
    # to target a JDK the same or lower than both.
    javacopts = [
        "--release",
        "8",
    ],
    resource_strip_prefix = "tools/base/common/version",
    resources = ["//tools/base/common/version/com/android:version.properties"],
    visibility = ["//tools/base/common:__pkg__"],
)

java_test(
    name = "version_test",
    srcs = ["VersionTest.java"],
    jvm_flags = select({
        "//tools/base/bazel:release": [
            "-Dtest.version.baseVersion=" + RELEASE_BASE_VERSION,
            "-Dtest.version.buildVersion=" + RELEASE_BUILD_VERSION,
        ],
        "//conditions:default": [
            "-Dtest.version.baseVersion=" + DEV_BASE_VERSION,
            "-Dtest.version.buildVersion=" + DEV_BUILD_VERSION,
        ],
    }),
    test_class = "com.android.VersionTest",
    deps = [
        ":version",
        "@maven//:junit.junit",
    ],
)
