# Conditions related to Continuous Integration builds.

config_setting(
    name = "studio-linux",
    flag_values = {
        "//tools/base/bazel/ci:ab_target": "studio-linux",
    },
    visibility = ["//visibility:public"],
)

config_setting(
    name = "studio-linux_large",
    flag_values = {
        "//tools/base/bazel/ci:ab_target": "studio-linux_large",
    },
    visibility = ["//visibility:public"],
)

config_setting(
    name = "ci-environment",
    flag_values = {
        "//tools/base/bazel/ci:is_ci": "True",
    },
    visibility = ["//visibility:public"],
)
