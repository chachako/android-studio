GRADLE_PROPERTIES = select({
    "//tools/base/bazel:release": {
        "hybrid-build-embedded-in-bazel": "true",
        "release": "true",
    },
    "//conditions:default": {
        "hybrid-build-embedded-in-bazel": "true",
    },
})
