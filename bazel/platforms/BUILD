load(
    "@bazel_toolchains//rules/exec_properties:exec_properties.bzl",
    "create_rbe_exec_properties_dict",
)
load("@bazel_skylib//lib:dicts.bzl", "dicts")

package(default_visibility = ["//visibility:public"])

config_setting(
    name = "macos-arm64",
    constraint_values = [
        "@platforms//os:macos",
        "@platforms//cpu:arm64",
    ],
)

config_setting(
    name = "macos-x86_64",
    constraint_values = [
        "@platforms//os:macos",
        "@platforms//cpu:x86_64",
    ],
)

platform(
    name = "linux",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
        "@bazel_tools//tools/cpp:clang",
    ],
    exec_properties = dicts.add(
        create_rbe_exec_properties_dict(
            container_image = "docker://gcr.io/google.com/android-studio-alphasource/rbe-ubuntu22-as@sha256:bc96d9c2b2508641c1bc300ed38b1157b3dde53f77e2b0358740da0196795d36",
            docker_add_capabilities = "SYS_NICE",
            docker_network = "off",
            docker_privileged = True,  # This is required to support emulators/nested KVM.
            docker_use_urandom = True,
            labels = {"machine-size": "default"},
            os_family = "Linux",
        ),
        # cache-silo-key: this is essentially a "salt" added to the remote cache key.
        # Change it to a new value when we want to create a new cache from scratch.
        {"cache-silo-key": "20211108"},
    ),
)

platform(
    name = "windows",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:windows",
    ],
    exec_properties = create_rbe_exec_properties_dict(
        container_image = "docker://gcr.io/google.com/android-studio-alphasource/rbe-windows2019-as@sha256:1ad1aa4e016b0b9ee41e5c0d587c3fc51033485b0242fb7ee9fdd242c7598879",
        docker_network = "off",
        labels = {"machine-size": "default"},
        os_family = "Windows",
    ),
)

constraint_setting(
    name = "macos_version",
)

constraint_value(
    name = "macos_latest",
    constraint_setting = ":macos_version",
)

platform(
    name = "macos",
    constraint_values = [
        "@platforms//os:macos",
        ":macos_latest",
    ],
    # N.B. we pick the CPU based on the host (x86_64 or arm64).
    parents = ["@local_config_platform//:host"],
)

constraint_value(
    name = "macos_10.13",
    constraint_setting = ":macos_version",
)

platform(
    name = "macpro10.13",
    constraint_values = [":macos_10.13"],
    parents = [":macos"],
)
