load("//tools/base/bazel/avd:avd.bzl", "avd", "DEFAULT_AVD_IMAGE")

# This test demonstrates running an emulator on RBE and using it for a Java
# test.
# Note the attribute: tags = ["no_mac", "no_windows"]. This is because emulator
# tests only run on Linux.
avd(
    name = "emulator_test_device",
    image = select({
        # For now, we tie the choice of Emulator ABI to the host ABI. However, we should split this.
        "//tools/base/bazel:darwin_arm64": "@system_image_android-33_default_arm64//:arm64-android-33-images",
        "//conditions:default": DEFAULT_AVD_IMAGE,
    }),
)

java_test(
    name = "emulator_test",
    srcs = ["EmulatorTest.java"],
    data = [
        ":emulator_test_device",
        "//prebuilts/studio/sdk:platform-tools",
    ],
    tags = [
        # This test passes on mac-arm64, but there is no label to make it run only on mac-arm64.
        "no_mac",
        "no_windows",
    ],
    test_class = "EmulatorTest",
    deps = [
        "//tools/base/bazel/avd:emulator_rule",
        "@maven//:junit.junit",
    ],
)
