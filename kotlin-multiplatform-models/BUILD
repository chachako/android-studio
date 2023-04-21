load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob(["src/main/**/*.kt"]) + ["build.gradle"],
    visibility = ["//tools/base/build-system:__pkg__"],
)

kotlin_library(
    name = "kotlin-multiplatform-models",
    srcs = glob([
        "src/main/kotlin/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/kotlin-multiplatform-models/proto:kotlin_multiplatform_android_models_proto_for_agp",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.kotlin-multiplatform-models",
    srcs = ["src/main/kotlin"],
    iml_files = ["android.kotlin-multiplatform-models.iml"],
    jvm_target = "8",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/.idea/libraries:kotlin-multiplatform-android-models-proto",
    ],
)
