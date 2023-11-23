load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/bazel:utils.bzl", "fileset")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.sdk-common",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.sdk-common.iml"],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    resources = ["src/main/resources"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/base/sdk-common/gradle:studio.android.sdktools.sdk-common.gradle",
        "//tools/adt/idea/.idea/libraries:kxml2",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/sdk-common:aia-proto",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/sdk-common/gradle:studio.android.sdktools.sdk-common.gradle[module]",
        "//tools/adt/idea/.idea/libraries:kxml2",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/adt/idea/.idea/libraries:equalsverifier[test]",
        "//tools/adt/idea/.idea/libraries:javax-inject",
        "//tools/adt/idea/.idea/libraries:aapt-proto",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:studio-proto",
    ],
)

fileset(
    name = "tools.resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "src/main/resources/": "",
    },
)

maven_library(
    name = "tools.sdk-common",
    srcs = glob(["src/main/java/**"]),
    bundled_deps = [
        "//tools/base/sdk-common/aia-manifest-jar-generator:tools.proto",
        "//tools/base/sdk-common/gradle:tools.sdk-common.gradle.lib",
    ],
    coordinates = "com.android.tools:sdk-common",
    description = "sdk-common library used by other Android tools libraries.",
    module_name = "sdk-common",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/sdk-common",
    resources = [":tools.resources"],
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/base/build-system/aapt2-proto",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:javax.inject.javax.inject",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.bouncycastle.bcpkix-jdk15on",
        "@maven//:org.bouncycastle.bcprov-jdk15on",
        "@maven//:org.glassfish.jaxb.jaxb-runtime",
        "@maven//:org.jetbrains.intellij.deps.trove4j",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

java_binary(
    name = "update_google_maven_repository_cache",
    testonly = 1,
    main_class = "com.android.ide.common.repository.UpdateGoogleMavenRepositoryCache",
    runtime_deps = [":tools.tests.testlib"],
)

kotlin_test(
    name = "tools.tests",
    srcs = glob([
        "src/test/java/**",
        "gradle/src/test/java/**",
    ]),
    data = ["//prebuilts/studio/sdk:platform-tools"],
    friends = [":tools.sdk-common"],
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "@maven//:org.bouncycastle.bcpkix-jdk15on",
    ],
    deps = [
        ":tools.sdk-common",
        "//tools/base/annotations",
        "//tools/base/build-system/aapt2-proto",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/repository:tools.testlib",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.jimfs.jimfs",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:com.google.truth.truth",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:nl.jqno.equalsverifier.equalsverifier",
        "@maven//:org.jetbrains.intellij.deps.trove4j",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.mockito.mockito-core",
    ],
)

kotlin_library(
    name = "tools.sdk-common.test.util",
    srcs = [
        "src/test/java/com/android/ide/common/resources/RecordingLogger.java",
    ],
    visibility = [
        "//tools/adt/idea/rendering:__pkg__",
    ],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "@maven//:com.google.guava.guava",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "aia-proto",
    jars = ["//tools/base/sdk-common/aia-manifest-jar-generator:proto.jar"],
    visibility = ["//visibility:public"],
)
