load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/bazel:utils.bzl", "fileset")
load("//tools/base/common:version.bzl", "BASE_VERSION")

filegroup(
    name = "agp_gradle_build_files",
    srcs = [
        # As the Gradle builds need to be able to run outside of Bazel,
        # they read the version file directly
        "release_version.bzl",
    ],
    visibility = [
        "//tools/base/build-system:__pkg__",
        "//tools/base/firebase/testlab/testlab-gradle-plugin:__pkg__",
        "//tools/base/preview/screenshot/preview-screenshot-gradle-plugin:__pkg__",
        "//tools/base/utp:__pkg__",
        "//tools/data-binding:__pkg__",
    ],
)

fileset(
    name = "TestData",
    srcs = glob(["src/test/data/**"]),
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.common",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.common.iml"],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    # do not sort: must match IML order
    resources = ["src/main/resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/base/common:version",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:version",
    ],
)

fileset(
    name = "tools.common.resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "src/main/resources/": "",
    },
)

maven_library(
    name = "tools.common",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/com/android/*.java",
        "src/main/java/com/android/ide/common/blame/*.java",
        "src/main/java/com/android/io/*.java",
        "src/main/java/com/android/prefs/*.java",
        "src/main/java/com/android/sdklib/*.java",
        "src/main/java/com/android/support/*.java",
        "src/main/java/com/android/tools/proguard/*.java",
        "src/main/java/com/android/utils/*.java",
        "src/main/java/com/android/utils/concurrency/*.java",
        "src/main/java/com/android/xml/*.java",
        "src/main/java/com/android/xml/sax/*.java",
    ]),
    bundled_deps = [
        "//tools/base/common/version",
    ],
    coordinates = "com.android.tools:common",
    description = "common library used by other Android tools libraries.",
    module_name = "common",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/common",
    resources = [
        ":tools.common.resources",
    ],
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    exports = [
        "//tools/base/annotations",
        "@maven//:com.google.guava.guava",
    ],
    deps = [
        "@maven//:net.java.dev.jna.jna-platform",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

kotlin_test(
    name = "tools.tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/com/android/ide/common/blame/*.java",
        "src/test/java/com/android/io/*.java",
        "src/test/java/com/android/prefs/*.java",
        "src/test/java/com/android/support/*.java",
        "src/test/java/com/android/tools/proguard/*.java",
        "src/test/java/com/android/utils/*.java",
        "src/test/java/com/android/utils/concurrency/*.java",
    ]),
    data = [
        ":TestData",
    ],
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.common",
        ":tools.fixtures",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.jetbrains.kotlin.kotlin-test",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.common.tests",
    data = [
        ":TestData",
    ],
    iml_files = ["android.sdktools.common.tests.iml"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/base/annotations:studio.android.sdktools.android-annotations"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/.idea/libraries:gson[test]",
    ],
)

kotlin_library(
    name = "tools.fixtures",
    srcs = glob([
        "src/testFixtures/java/**/*.kt",
        "src/testFixtures/java/com/android/prefs/*.java",
    ]),
    resources = glob(["src/testFixtures/resources/**"]),
    visibility = ["//visibility:public"],  # contains com.android.repository.testframework
    deps = [
        ":tools.common",
        "//tools/base/annotations",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.common.testfixtures",
    srcs = ["src/testFixtures/java"],
    iml_files = ["android.sdktools.common.testfixtures.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/base/annotations:studio.android.sdktools.android-annotations"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "version",
    jars = ["//tools/base/common/version:libversion.jar"],
    visibility = ["//visibility:public"],
)
