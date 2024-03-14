load(
    "//tools/base/common:version.bzl",
    "DEV_BASE_VERSION",
    "DEV_BUILD_VERSION",
    "RELEASE_BASE_VERSION",
    "RELEASE_BUILD_VERSION",
)

java_library(
    name = "agp-version",
    srcs = ["Version.java"],
    # This compiled class is used in both the studio
    # (//tools/base/common:studio.android.sdktools.common) and tools
    # (//tools/base/common:tools.common) builds, and so is constrained
    # to target a JDK the same or lower than both.
    # (but is no longer injected into AGP from studio (b/306442910), so is not
    # constrained  to be the lowest java version supported for syncing a Gradle
    # project)
    javacopts = [
        "--release",
        "11",
    ],
    resource_strip_prefix = "tools/base/common/agp-version",
    resources = ["//tools/base/common/agp-version/com/android:version.properties"],
    visibility = [
        "//tools/adt/idea/.idea/libraries:__pkg__",
        "//tools/base/common:__pkg__",
    ],
)

java_test(
    name = "agp-version_test",
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
        ":agp-version",
        "@maven//:junit.junit",
    ],
)
