# Conditions related to Continuous Integration builds.

config_setting(
    name = "studio-linux_large",
    flag_values = {
        "//tools/base/bazel/ci:ab_target": "studio-linux_large",
    },
    visibility = ["//visibility:public"],
)

# This condition indicates Bazel is being run via the CI script.
# It does not imply that the build is being run on a CI machine.
config_setting(
    name = "ci",
    flag_values = {
        "//tools/base/bazel/ci:is_ci": "True",
    },
    visibility = ["//visibility:public"],
)
