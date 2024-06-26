load("//tools/base/bazel/avd:avd.bzl", "avd", "DEFAULT_AVD_IMAGE")

# This test demonstrates running an emulator on RBE and using it for a Java
# test.
avd(
    name = "emulator_test_device",
    image = select({
        # For now, we tie the choice of Emulator ABI to the host ABI. However, we should split this.
        "//tools/base/bazel/platforms:macos-arm64": "@system_image_android-33_default_arm64//:arm64-android-33-images",
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
        "ci:studio-mac-arm",
        "cpu:2",
        "noci:studio-win",
    ],
    test_class = "EmulatorTest",
    deps = [
        "//tools/base/bazel/avd:emulator_rule",
        "@maven//:junit.junit",
    ],
)
