load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.debugger-tests",
    # do not sort: must match IML order
    srcs = [
        "resources/src",
        "src",
    ],
    iml_files = ["debugger-tests.iml"],
    resources = ["resources/res"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//prebuilts/tools/common/m2:kotlinx-cli-jvm-0.3.1",
    ],
)

# Build jar file from test resources
java_binary(
    name = "update-golden",
    jvm_flags = ["--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"],
    main_class = "com.android.tools.debuggertests.UpdateGoldenKt",
    runtime_deps = [
        ":studio.debugger-tests",
    ],
)
